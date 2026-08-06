package tgb.cryptoexchange.orders.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tgb.cryptoexchange.orders.dto.OrderDTO;
import tgb.cryptoexchange.orders.entity.Order;
import tgb.cryptoexchange.orders.exceptions.BaseException;

import java.net.URI;
import java.time.Instant;

@Slf4j
@Component
public class CallbackSender {

    private final ObjectMapper objectMapper;

    private final ApiClientsGrpcService apiClientsGrpcService;

    private final WebClient webClient;

    public CallbackSender(ObjectMapper objectMapper, ApiClientsGrpcService apiClientsGrpcService,
            WebClient.Builder webClientBuilder) {
        this.objectMapper = objectMapper;
        this.apiClientsGrpcService = apiClientsGrpcService;
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
                ? apiClientsGrpcService.getClientById(orderDTO.getClientId())
                .map(clientDto -> {
                    orderDTO.setCallbackUrl(clientDto.getCallbackUrl());
                    return clientDto.getCallbackUrl();
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
        try {
            URI uri = URI.create(callbackUrl);
            String requestPath = uri.getRawPath();
            if (requestPath == null || requestPath.isEmpty()) {
                requestPath = "/";
            }

            String httpMethod = "POST";

            String content = objectMapper.writeValueAsString(orderDTO);
            String dataToSign = "%s|%s|%d|%s".formatted(httpMethod, requestPath, timestamp, content);
            log.debug("Сформирована строка для gRPC подписи: {}", dataToSign);
            return apiClientsGrpcService.createSignature(orderDTO.getClientId(), dataToSign);

        } catch (Exception e) {
            log.error("Ошибка при подготовке данных для gRPC подписи", e);
            return Mono.error(new BaseException("Failed to prepare signature data: " + e.getMessage()));
        }
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
