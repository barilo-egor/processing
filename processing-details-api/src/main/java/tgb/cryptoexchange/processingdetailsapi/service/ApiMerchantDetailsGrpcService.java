package tgb.cryptoexchange.processingdetailsapi.service;

import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tgb.cryptoexchange.grpc.generated.GetDetailsGrpc;
import tgb.cryptoexchange.grpc.generated.GetDetailsResponseGrpc;
import tgb.cryptoexchange.grpc.generated.MerchantDetailsServiceGrpc;
import tgb.cryptoexchange.processingdetailsapi.constants.Metrics;
import tgb.cryptoexchange.processingdetailsapi.dto.ApiDetailsRequestDTO;
import tgb.cryptoexchange.processingdetailsapi.dto.ApiDetailsResponseDTO;
import tgb.cryptoexchange.processingdetailsapi.dto.ClientByApiKeyDTO;
import tgb.cryptoexchange.processingdetailsapi.exceptions.BaseException;
import tgb.cryptoexchange.processingdetailsapi.exceptions.MerchantDetailsNotFoundException;
import tgb.cryptoexchange.processingdetailsapi.mapper.DetailsMapper;

import static tgb.cryptoexchange.processingdetailsapi.constants.Metrics.CLIENT_ID;

@Service
@Slf4j
public class ApiMerchantDetailsGrpcService extends GrpcService {

    public static final String STATUS = "status";

    private final MerchantDetailsServiceGrpc.MerchantDetailsServiceFutureStub detailsFutureStub;

    private final DetailsMapper detailsMapper;

    private final MeterRegistry meterRegistry;

    public ApiMerchantDetailsGrpcService(DetailsMapper detailsMapper, MeterRegistry meterRegistry,
            MerchantDetailsServiceGrpc.MerchantDetailsServiceFutureStub detailsFutureStub) {
        this.detailsFutureStub = detailsFutureStub;
        this.detailsMapper = detailsMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Получает реквизиты мерчанта по деталям запроса через gRPC.
     *
     * @param requestDTO параметры запроса реквизитов.
     * @return {@link ApiDetailsResponseDTO} с найденными реквизитами.
     * @throws MerchantDetailsNotFoundException если реквизиты не найдены (gRPC NOT_FOUND).
     * @throws BaseException                    при системных ошибках gRPC или сбоях сети.
     */
    public ApiDetailsResponseDTO getDetails(ApiDetailsRequestDTO requestDTO, ClientByApiKeyDTO client) {
        GetDetailsGrpc request = detailsMapper.detailsRequestDTOToGrpc(requestDTO);
        ListenableFuture<GetDetailsResponseGrpc> grpcFuture = detailsFutureStub.getDetails(request);
        try {
            GetDetailsResponseGrpc response = toCompletableFuture(grpcFuture).join();
            return detailsMapper.grpcResponseToDTO(response);
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof StatusRuntimeException statusEx) {
                com.google.rpc.Status status = io.grpc.protobuf.StatusProto.fromThrowable(statusEx);
                if (status != null && status.getCode() == com.google.rpc.Code.NOT_FOUND_VALUE) {
                    log.warn("Не найдены реквизиты для {}", requestDTO);
                    meterRegistry.counter(Metrics.DETAILS_REQUEST_NO_DETAILS, CLIENT_ID,
                            String.valueOf(client.getClientId()), STATUS, "not_found").increment();
                    throw new MerchantDetailsNotFoundException();
                }
                log.error("Системная gRPC ошибка от merchant-details: код={}", statusEx.getStatus().getCode());
                meterRegistry.counter(Metrics.DETAILS_REQUEST_ERROR, CLIENT_ID,
                        String.valueOf(client.getClientId()), STATUS, "error").increment();
                throw new BaseException("gRPC service error");
            }
            log.error("Непредвиденная ошибка сети при вызове gRPC", ex);
            meterRegistry.counter(Metrics.DETAILS_REQUEST_ERROR, CLIENT_ID,
                    String.valueOf(client.getClientId()), STATUS, "error").increment();
            throw new BaseException("System connection error");
        }
    }

}
