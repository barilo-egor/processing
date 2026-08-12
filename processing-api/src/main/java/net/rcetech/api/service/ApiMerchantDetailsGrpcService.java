package net.rcetech.api.service;

import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import net.rcetech.grpc.generated.GetDetailsGrpc;
import net.rcetech.grpc.generated.GetDetailsResponseGrpc;
import net.rcetech.grpc.generated.MerchantDetailsServiceGrpc;
import net.rcetech.api.constants.Metrics;
import net.rcetech.api.dto.ApiDetailsRequestDTO;
import net.rcetech.api.dto.ApiDetailsResponseDTO;
import net.rcetech.api.dto.ClientByApiKeyDTO;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.api.exceptions.MerchantDetailsNotFoundException;
import net.rcetech.api.mapper.DetailsMapper;

import static net.rcetech.api.constants.Metrics.CLIENT_ID;

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
