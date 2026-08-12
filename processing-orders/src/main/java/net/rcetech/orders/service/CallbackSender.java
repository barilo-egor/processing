package net.rcetech.orders.service;

import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.meta.clients.dto.CreateSignatureDTO;
import net.rcetech.clients.service.ClientApi;
import net.rcetech.meta.orders.dto.OrderDTO;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.orders.exceptions.BaseException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

@Slf4j
@Component
public class CallbackSender {

    private final ObjectMapper objectMapper;

    private final ClientApi clientApi;

    private final WebClient webClient;

    public CallbackSender(ObjectMapper objectMapper, ClientApi clientApi,
            WebClient.Builder webClientBuilder) {
        this.objectMapper = objectMapper;
        this.clientApi = clientApi;
        this.webClient = webClientBuilder.build();
    }

    /**
     * Слушатель события успешного обновления {@link Order#getStatus()}
     * <p>
     * Метод срабатывает асинхронно после успешного коммита транзакции БД
     * ({@link TransactionPhase#AFTER_COMMIT}). Запускает реактивную цепочку
     * получения URL-адреса, генерации цифровой подписи через gRPC и отправки callback-уведомления.
     *
     * @param orderDTO данные заказа, для которого необходимо отправить callback
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCreatedEvent(OrderDTO orderDTO) {
        log.info("Транзакция успешно закоммичена. Пост-логика отправки callback для заказа {}", orderDTO.getId());
        executePostOrderStatusUpdate(orderDTO);
    }

    private void executePostOrderStatusUpdate(OrderDTO orderDTO) {
        Mono<String> callbackUrlMono = StringUtils.isBlank(orderDTO.getCallbackUrl())
                ? Mono.fromSupplier(() -> clientApi.getClientById(orderDTO.getClientId()))
                .map(clientDto -> {
                    orderDTO.setCallbackUrl(clientDto.callbackUrl());
                    return clientDto.callbackUrl();
                })
                : Mono.just(orderDTO.getCallbackUrl());

        callbackUrlMono.flatMap(callbackUrl -> {
                    if (StringUtils.isBlank(callbackUrl)) {
                        log.warn("Пропущена отправка callback: URL пуст для order {}", orderDTO.getId());
                        return Mono.empty();
                    }
                    long timestamp = Instant.now().getEpochSecond();
                    return requestClientSignature(orderDTO, callbackUrl, timestamp)
                            .flatMap(signature -> sendPostOrderStatusUpdate(orderDTO, signature, timestamp));
                })
                .subscribe(
                        success -> log.debug("Процесс отправки callback для заказа {} успешно завершен",
                                orderDTO.getId()),
                        error -> log.error("Критическая ошибка при обработке callback для заказа {}", orderDTO.getId(),
                                error)
                );
    }

    private Mono<String> requestClientSignature(OrderDTO orderDTO, String callbackUrl, long timestamp) {
        return Mono.fromSupplier(() -> {
            try {
                URI uri = URI.create(callbackUrl);
                String requestPath = uri.getRawPath();
                if (requestPath == null || requestPath.isEmpty()) {
                    requestPath = "/";
                }

                String httpMethod = "POST";
                String content = objectMapper.writeValueAsString(orderDTO);
                String dataToSign = "%s|%s|%d|%s".formatted(httpMethod, requestPath, timestamp, content);
                log.debug("Сформирована строка для подписи: {}", dataToSign);

                return clientApi.createSignature(new CreateSignatureDTO(orderDTO.getClientId(), dataToSign));
            } catch (Exception e) {
                log.error("Ошибка при подготовке данных или формировании подписи", e);
                throw new BaseException("Failed to prepare signature data: " + e.getMessage());
            }
        });
    }

    private Mono<Void> sendPostOrderStatusUpdate(OrderDTO orderDTO, String signature, long timestamp) {
        if (StringUtils.isBlank(orderDTO.getCallbackUrl())) {
            log.warn("Пропущена отправка callback: URL пуст для order {}", orderDTO.getId());
            return Mono.empty();
        }
        log.info("Запуск отправки callback для order {}", orderDTO.getId());
        return webClient.post()
                .uri(orderDTO.getCallbackUrl())
                .header("Signature", signature)
                .header("X-Timestamp", String.valueOf(timestamp))
                .bodyValue(orderDTO)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(response -> log.info("Callback для order {} успешно отправлен по адресу {}",
                        orderDTO.getId(), orderDTO.getCallbackUrl()))
                .doOnError(error -> log.error("Не удалось доставить callback на {} для order {}: {}",
                        orderDTO.getCallbackUrl(), orderDTO.getId(), error.getMessage()))
                .then();
    }

}
