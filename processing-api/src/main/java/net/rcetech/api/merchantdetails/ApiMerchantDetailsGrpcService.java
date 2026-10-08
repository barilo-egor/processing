package net.rcetech.api.merchantdetails;

import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.grpc.generated.ApiDetailsRequestServiceGrpc;
import net.rcetech.grpc.generated.DetailsRequestGrpc;
import net.rcetech.grpc.generated.DetailsResponseGrpc;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.exception.MerchantDetailsNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class ApiMerchantDetailsGrpcService {

    private final ApiDetailsRequestServiceGrpc.ApiDetailsRequestServiceBlockingStub detailsBlockingStub;

    private final DetailsMapper detailsMapper;

    private final String applicationName;

    public ApiMerchantDetailsGrpcService(DetailsMapper detailsMapper,
                                         ApiDetailsRequestServiceGrpc.ApiDetailsRequestServiceBlockingStub detailsBlockingStub,
                                         @Value("${spring.application.name}") String applicationName) {
        this.detailsBlockingStub = detailsBlockingStub;
        this.detailsMapper = detailsMapper;
        this.applicationName = applicationName;
    }

    /**
     * Получает реквизиты мерчанта по clientId и деталям запроса через gRPC.
     *
     * @return {@link ApiDetailsResponse} с найденными реквизитами.
     * @throws MerchantDetailsNotFoundException если реквизиты не найдены (gRPC NOT_FOUND).
     * @throws BaseException                    при системных ошибках gRPC или сбоях сети.
     */
    public ApiDetailsResponse getDetails(UUID clientId, UUID orderId, CreateOrderRequest clientOrderRequest,
                                         Integer timeoutSeconds) {
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
                    .build();
            DetailsResponseGrpc grpcResponse = detailsBlockingStub.detailsRequest(
                    detailsRequestGrpc
            );
            return detailsMapper.grpcResponseToDTO(grpcResponse);
        } catch (StatusRuntimeException statusException) {
            Status status = StatusProto.fromThrowable(statusException);
            int code = status != null
                    ? status.getCode()
                    : statusException.getStatus().getCode().value();
            if (code == Code.NOT_FOUND_VALUE) {
                log.info("Не найдены реквизиты для {}", clientOrderRequest);
                throw new MerchantDetailsNotFoundException();
            } else {
                throw statusException;
            }
        } catch (Exception ex) {
            throw new RuntimeException("Непредвиденная ошибка: " + ex.getMessage(), ex);
        }
    }

    /**
     * Получает реквизиты мерчанта по clientId и деталям запроса с возможностью запроса тестовых реквизитов.
     *
     * @param clientId           идентификатор клиента
     * @param orderId            идентификатор ордера
     * @param clientOrderRequest запрос на создание ордера
     * @return {@link ApiDetailsResponse} с найденными реквизитами
     */
    public ApiDetailsResponse getTestDetails(UUID clientId, UUID orderId, CreateOrderRequest clientOrderRequest) {
        AtomicReference<ApiDetailsResponse> responseRef = new AtomicReference<>();
        TestDetailsClientInterceptor.runWithTestDetails(() ->
                responseRef.set(getDetails(clientId, orderId, clientOrderRequest, 10))
        );
        return responseRef.get();
    }

    public void getCallback(MerchantCallbackDTO merchantCallbackDTO) {
        try {
            detailsBlockingStub.merchantCallbackRequest(
                    detailsMapper.merchantCallbackDTOToGrpc(merchantCallbackDTO)
            );
        } catch (Exception ex) {
            throw new RuntimeException("Непредвиденная ошибка: " + ex.getMessage(), ex);
        }
    }

}
