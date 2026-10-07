package net.rcetech.support.service;

import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.grpc.generated.MerchantHistoryRequestGrpc;
import net.rcetech.grpc.generated.MerchantHistoryResponseGrpc;
import net.rcetech.grpc.generated.MerchantHistoryServiceGrpc;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.support.dto.MerchantHistoryFilter;
import net.rcetech.meta.support.dto.MerchantHistoryResponseDTO;
import net.rcetech.support.mapper.MerchantHistoryMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Реализация сервиса истории мерчантов через gRPC.
 */
@Slf4j
@Service
@Profile("!merchant-history-stub")
public class MerchantHistoryServiceImpl implements MerchantHistoryService {

    private final MerchantHistoryServiceGrpc.MerchantHistoryServiceBlockingStub historyBlockingStub;

    private final MerchantHistoryMapper merchantHistoryMapper;

    public MerchantHistoryServiceImpl(
            MerchantHistoryServiceGrpc.MerchantHistoryServiceBlockingStub historyBlockingStub,
            MerchantHistoryMapper merchantHistoryMapper) {
        this.historyBlockingStub = historyBlockingStub;
        this.merchantHistoryMapper = merchantHistoryMapper;
    }

    @Override
    public List<MerchantHistoryResponseDTO> getHistory(MerchantHistoryFilter filter, Pageable pageable) {
        MerchantHistoryRequestGrpc request = merchantHistoryMapper.filterToGrpc(filter, pageable);
        try {
            MerchantHistoryResponseGrpc response = historyBlockingStub.getHistory(request);
            return merchantHistoryMapper.grpcToDtoList(response);
        } catch (Exception ex) {
            throw mapGrpcException(ex);
        }
    }

    /**
     * Преобразует исключение gRPC-вызова в прикладное runtime-исключение.
     *
     * @param ex перехваченное исключение
     * @return runtime-исключение для проброса вызывающему коду
     */
    private RuntimeException mapGrpcException(Exception ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof StatusRuntimeException statusEx) {
            log.error("Системная gRPC ошибка от merchant-history при {}: код={}",
                    "getHistory", statusEx.getStatus().getCode());
            return new BaseException("gRPC service error", statusEx);
        }
        log.error("Непредвиденная ошибка сети при вызове gRPC ({})", "getHistory", ex);
        return new BaseException("System connection error", ex);
    }

}
