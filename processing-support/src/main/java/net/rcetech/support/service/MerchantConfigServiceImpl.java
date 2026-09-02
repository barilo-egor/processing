package net.rcetech.support.service;

import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.grpc.generated.*;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.support.dto.MerchantConfigResponseDTO;
import net.rcetech.meta.support.dto.MerchantConfigUpdateDTO;
import net.rcetech.meta.util.GrpcService;
import net.rcetech.support.mapper.MerchantConfigMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Реализация сервиса конфигураций мерчантов через gRPC.
 */
@Slf4j
@Service
public class MerchantConfigServiceImpl extends GrpcService implements MerchantConfigService {

    private final MerchantConfigServiceGrpc.MerchantConfigServiceFutureStub configFutureStub;

    private final MerchantConfigMapper merchantConfigMapper;

    public MerchantConfigServiceImpl(MerchantConfigServiceGrpc.MerchantConfigServiceFutureStub configFutureStub,
            MerchantConfigMapper merchantConfigMapper) {
        this.configFutureStub = configFutureStub;
        this.merchantConfigMapper = merchantConfigMapper;
    }

    @Override
    public List<MerchantConfigResponseDTO> findAll(UUID ownerId) {
        FindAllMerchantConfigsRequestGrpc request = FindAllMerchantConfigsRequestGrpc.newBuilder()
                .setOwnerId(ownerId.toString())
                .build();
        ListenableFuture<FindAllMerchantConfigsResponseGrpc> grpcFuture = configFutureStub.findAll(request);
        try {
            FindAllMerchantConfigsResponseGrpc response = toCompletableFuture(grpcFuture).join();
            return merchantConfigMapper.merchantConfigsToList(response);
        } catch (Exception ex) {
            throw mapGrpcException(ex, "findAll");
        }
    }

    @Override
    public MerchantConfigResponseDTO update(Long id, MerchantConfigUpdateDTO updateDTO) {
        UpdateMerchantConfigRequestGrpc request = merchantConfigMapper.updateDtoToGrpc(id, updateDTO);
        ListenableFuture<MerchantConfigResponseGrpc> grpcFuture = configFutureStub.update(request);
        try {
            MerchantConfigResponseGrpc response = toCompletableFuture(grpcFuture).join();
            return merchantConfigMapper.grpcToDto(response);
        } catch (Exception ex) {
            throw mapGrpcException(ex, "update");
        }
    }

    /**
     * Преобразует исключение gRPC-вызова в прикладное runtime-исключение.
     *
     * @param ex        перехваченное исключение
     * @param operation имя операции для логирования
     * @return runtime-исключение для проброса вызывающему коду
     */
    private RuntimeException mapGrpcException(Exception ex, String operation) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof StatusRuntimeException statusEx) {
            log.error("Системная gRPC ошибка от api-merchant-details при {}: код={}",
                    operation, statusEx.getStatus().getCode());
            return new BaseException("gRPC service error");
        }
        log.error("Непредвиденная ошибка сети при вызове gRPC ({})", operation, ex);
        return new BaseException("System connection error");
    }

}
