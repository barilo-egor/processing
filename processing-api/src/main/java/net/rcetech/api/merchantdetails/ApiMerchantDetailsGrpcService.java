package net.rcetech.api.merchantdetails;

import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.Context;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.grpc.generated.ApiDetailsRequestServiceGrpc;
import net.rcetech.grpc.generated.DetailsRequestGrpc;
import net.rcetech.grpc.generated.DetailsResponseGrpc;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.incidents.DetailsSearchErrorEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ApiMerchantDetailsGrpcService {

    private final ApiDetailsRequestServiceGrpc.ApiDetailsRequestServiceBlockingStub detailsBlockingStub;

    private final DetailsMapper detailsMapper;

    private final String applicationName;

    private final ApplicationEventPublisher eventPublisher;

    private final Map<UUID, Context.CancellableContext> searches = new ConcurrentHashMap<>();

    public ApiMerchantDetailsGrpcService(DetailsMapper detailsMapper,
                                         ApiDetailsRequestServiceGrpc.ApiDetailsRequestServiceBlockingStub detailsBlockingStub,
                                         @Value("${spring.application.name}") String applicationName,
                                         ApplicationEventPublisher eventPublisher) {
        this.detailsBlockingStub = detailsBlockingStub;
        this.detailsMapper = detailsMapper;
        this.applicationName = applicationName;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Получает реквизиты мерчанта по clientId и деталям запроса через gRPC.
     *
     * @return {@link ApiDetailsResponse} с найденными реквизитами или пустой Optional, если реквизиты не найдены
     * (gRPC NOT_FOUND), истёк таймаут поиска (gRPC DEADLINE_EXCEEDED), поиск отменён через {@link #cancelSearch(UUID)} или при поиске произошла ошибка. Об ошибке
     * создаётся инцидент, если необработанного инцидента такого типа ещё нет.
     */
    public Optional<ApiDetailsResponse> getDetails(UUID clientId, UUID orderId, CreateOrderRequest clientOrderRequest,
                                         Integer timeoutSeconds) {
        Context.CancellableContext context = Context.current().withCancellation();
        searches.put(orderId, context);
        try {
            UUID requestId = UUID.randomUUID();
            log.debug("Отправка запроса на реквизиты requestId={}, orderId={}: {}", requestId, orderId, clientOrderRequest);
            DetailsRequestGrpc detailsRequestGrpc = DetailsRequestGrpc.newBuilder()
                    .setRequestId(requestId.toString())
                    .setInternalId(orderId.toString())
                    .setUserId(clientOrderRequest.userId())
                    .setAmount(clientOrderRequest.amount())
                    .setWaitTimeout(timeoutSeconds)
                    .addAllRequestMethod(clientOrderRequest.methods().stream().map(Enum::name).toList())
                    .setOwnerId(clientId.toString())
                    .setInitiatorApp(applicationName)
                    .build();
            DetailsResponseGrpc grpcResponse = context.call(() -> detailsBlockingStub
                    .withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS)
                    .detailsRequest(detailsRequestGrpc));
            return Optional.of(detailsMapper.grpcResponseToDTO(grpcResponse));
        } catch (StatusRuntimeException statusException) {
            Status status = StatusProto.fromThrowable(statusException);
            int code = status != null
                    ? status.getCode()
                    : statusException.getStatus().getCode().value();
            if (code == Code.NOT_FOUND_VALUE) {
                log.info("Не найдены реквизиты по ордеру {}: {}", orderId, clientOrderRequest);
                return Optional.empty();
            }
            if (code == Code.DEADLINE_EXCEEDED_VALUE) {
                log.info("Истёк таймаут поиска реквизитов по ордеру {}: {}", orderId, clientOrderRequest);
                return Optional.empty();
            }
            if (code == Code.CANCELLED_VALUE && context.isCancelled()) {
                log.info("Поиск реквизитов по ордеру {} отменён.", orderId);
                return Optional.empty();
            }
            return searchError(orderId, "Неизвестная ошибка GRPC " + code, statusException);
        } catch (Exception ex) {
            return searchError(orderId, "Непредвиденная ошибка: " + ex.getMessage(), ex);
        } finally {
            searches.remove(orderId, context);
            context.cancel(null);
        }
    }

    public void cancelSearch(UUID orderId) {
        Context.CancellableContext context = searches.get(orderId);
        if (context != null) {
            log.info("Отмена поиска реквизитов по ордеру {}.", orderId);
            context.cancel(null);
        }
    }

    private Optional<ApiDetailsResponse> searchError(UUID orderId, String error, Exception ex) {
        long timestamp = System.currentTimeMillis();
        log.error("{} Ошибка поиска реквизитов по ордеру {}: {}", timestamp, orderId, error, ex);
        eventPublisher.publishEvent(new DetailsSearchErrorEvent(orderId, error, timestamp));
        return Optional.empty();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void getCallback(MerchantCallbackDTO merchantCallbackDTO) {
        try {
            detailsBlockingStub.merchantCallbackRequest(
                    detailsMapper.merchantCallbackDTOToGrpc(merchantCallbackDTO)
            );
        } catch (Exception ex) {
            throw new BaseException("Непредвиденная ошибка: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void cancelOrder(CancelOrderDTO cancelOrderDTO) {
        try {
            detailsBlockingStub.cancelOrderRequest(detailsMapper.cancelOrderDTOToGrpc(cancelOrderDTO));
        } catch (Exception ex) {
            throw new BaseException("Непредвиденная ошибка: " + ex.getMessage(), ex);
        }
    }

}
