package net.rcetech.api.service;

import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.api.dto.ApiDetailsResponse;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.api.mapper.DetailsMapper;
import net.rcetech.grpc.generated.ApiDetailsRequestServiceGrpc;
import net.rcetech.grpc.generated.DetailsResponseGrpc;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.exception.MerchantDetailsNotFoundException;
import net.rcetech.meta.util.GrpcService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class ApiMerchantDetailsGrpcService extends GrpcService {

    private final ApiDetailsRequestServiceGrpc.ApiDetailsRequestServiceBlockingStub detailsBlockingStub;

    private final DetailsMapper detailsMapper;

    public ApiMerchantDetailsGrpcService(DetailsMapper detailsMapper,
            ApiDetailsRequestServiceGrpc.ApiDetailsRequestServiceBlockingStub detailsBlockingStub) {
        this.detailsBlockingStub = detailsBlockingStub;
        this.detailsMapper = detailsMapper;
    }

    /**
     * Получает реквизиты мерчанта по деталям запроса через gRPC.
     *
     * @return {@link ApiDetailsResponse} с найденными реквизитами.
     * @throws MerchantDetailsNotFoundException если реквизиты не найдены (gRPC NOT_FOUND).
     * @throws BaseException                    при системных ошибках gRPC или сбоях сети.
     */
    public ApiDetailsResponse getDetails(UUID orderId, CreateOrderRequest clientOrderRequest) {
        try {
            UUID requestId = UUID.randomUUID();
            log.debug("Отправка запроса на реквизиты requestId={}, orderId={}: {}", requestId, orderId, clientOrderRequest);

            DetailsResponseGrpc grpcResponse = detailsBlockingStub.detailsRequest(
                    detailsMapper.detailsRequestDTOToGrpc(requestId, orderId, clientOrderRequest)
            );

            return detailsMapper.grpcResponseToDTO(grpcResponse);
        } catch (StatusRuntimeException statusException) {
            Status status = StatusProto.fromThrowable(statusException);

            int code = status != null ? status.getCode() : statusException.getStatus().getCode().value();
            if (code == Code.NOT_FOUND_VALUE) {
                log.info("Не найдены реквизиты для {}", clientOrderRequest);
                throw new MerchantDetailsNotFoundException();
            } else {
                throw new BaseException("Неизвестная ошибка GRPC " + code, statusException);
            }
        } catch (Exception ex) {
            throw new BaseException("Непредвиденная ошибка: " + ex.getMessage(), ex);
        }
    }

}
