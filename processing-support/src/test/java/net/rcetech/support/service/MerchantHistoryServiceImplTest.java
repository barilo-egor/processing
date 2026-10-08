package net.rcetech.support.service;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.rcetech.grpc.generated.MerchantHistoryRequestGrpc;
import net.rcetech.grpc.generated.MerchantHistoryResponseGrpc;
import net.rcetech.grpc.generated.MerchantHistoryServiceGrpc;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.support.dto.MerchantHistoryFilter;
import net.rcetech.meta.support.dto.MerchantHistoryResponseDTO;
import net.rcetech.support.mapper.MerchantHistoryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.net.ConnectException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantHistoryServiceImplTest {

    @Mock
    private MerchantHistoryServiceGrpc.MerchantHistoryServiceBlockingStub historyBlockingStub;

    @Mock
    private MerchantHistoryMapper merchantHistoryMapper;

    @InjectMocks
    private MerchantHistoryServiceImpl service;

    @Test
    @DisplayName("Должен успешно возвращать список истории мерчантов")
    void shouldReturnMerchantHistoryList_whenGetHistorySucceeds() {
        MerchantHistoryFilter filter = new MerchantHistoryFilter(
                "order-1", null, null, null, null, null, null, null, null
        );
        Pageable pageable = PageRequest.of(0, 20);

        MerchantHistoryRequestGrpc grpcRequest = MerchantHistoryRequestGrpc.newBuilder().build();
        MerchantHistoryResponseGrpc grpcResponse = MerchantHistoryResponseGrpc.newBuilder().build();

        var expectedDtoList = List.of(
                new MerchantHistoryResponseDTO(
                        "op-1", "act-1", "proc", Instant.now(), Merchant.ALFA_TEAM,
                        "order-1", 1000, 1000, "CARD", "details"
                )
        );

        when(merchantHistoryMapper.filterToGrpc(filter, pageable)).thenReturn(grpcRequest);
        when(historyBlockingStub.getHistory(grpcRequest)).thenReturn(grpcResponse);
        when(merchantHistoryMapper.grpcToDtoList(grpcResponse)).thenReturn(expectedDtoList);

        var result = service.getHistory(filter, pageable);

        assertThat(result).isNotNull().isEqualTo(expectedDtoList);
        verify(merchantHistoryMapper).filterToGrpc(filter, pageable);
        verify(historyBlockingStub).getHistory(grpcRequest);
        verify(merchantHistoryMapper).grpcToDtoList(grpcResponse);
    }

    @Test
    @DisplayName("Должен выбрасывать BaseException с сообщением 'gRPC service error' при UNAVAILABLE ошибке gRPC")
    void shouldThrowBaseException_forGrpcUnavailableError() {
        MerchantHistoryFilter filter = new MerchantHistoryFilter(
                null, null, null, null, null, null, null, null, null
        );
        Pageable pageable = Pageable.unpaged();

        MerchantHistoryRequestGrpc grpcRequest = MerchantHistoryRequestGrpc.newBuilder().build();
        StatusRuntimeException grpcException = Status.UNAVAILABLE
                .withDescription("Service unavailable")
                .asRuntimeException();

        when(merchantHistoryMapper.filterToGrpc(filter, pageable)).thenReturn(grpcRequest);
        when(historyBlockingStub.getHistory(grpcRequest)).thenThrow(grpcException);

        assertThatThrownBy(() -> service.getHistory(filter, pageable))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");
    }

    @Test
    @DisplayName("Должен выбрасывать BaseException с сообщением 'gRPC service error' при PERMISSION_DENIED ошибке gRPC")
    void shouldThrowBaseException_forGrpcPermissionDeniedError() {
        MerchantHistoryFilter filter = new MerchantHistoryFilter(
                null, null, null, null, null, null, null, null, null
        );
        Pageable pageable = Pageable.unpaged();

        MerchantHistoryRequestGrpc grpcRequest = MerchantHistoryRequestGrpc.newBuilder().build();
        StatusRuntimeException grpcException = Status.PERMISSION_DENIED
                .withDescription("Access denied")
                .asRuntimeException();

        when(merchantHistoryMapper.filterToGrpc(filter, pageable)).thenReturn(grpcRequest);
        when(historyBlockingStub.getHistory(grpcRequest)).thenThrow(grpcException);

        assertThatThrownBy(() -> service.getHistory(filter, pageable))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");
    }

    @Test
    @DisplayName("Должен выбрасывать BaseException с сообщением 'System connection error' при сетевой ошибке с cause")
    void shouldThrowBaseException_forNetworkErrors() {
        MerchantHistoryFilter filter = new MerchantHistoryFilter(
                null, null, null, null, null, null, null, null, null
        );
        Pageable pageable = Pageable.unpaged();

        MerchantHistoryRequestGrpc grpcRequest = MerchantHistoryRequestGrpc.newBuilder().build();
        StatusRuntimeException networkException = Status.UNAVAILABLE
                .withDescription("Connection refused")
                .withCause(new ConnectException("Connection refused"))
                .asRuntimeException();

        when(merchantHistoryMapper.filterToGrpc(filter, pageable)).thenReturn(grpcRequest);
        when(historyBlockingStub.getHistory(grpcRequest)).thenThrow(networkException);

        assertThatThrownBy(() -> service.getHistory(filter, pageable))
                .isInstanceOf(BaseException.class)
                .hasMessage("System connection error");
    }

    @Test
    @DisplayName("Должен выбрасывать BaseException при отмене вызова CANCELLED")
    void shouldThrowBaseException_forCancelledCall() {
        MerchantHistoryFilter filter = new MerchantHistoryFilter(
                null, null, null, null, null, null, null, null, null
        );
        Pageable pageable = Pageable.unpaged();

        MerchantHistoryRequestGrpc grpcRequest = MerchantHistoryRequestGrpc.newBuilder().build();
        StatusRuntimeException grpcException = Status.CANCELLED
                .withDescription("Context was cancelled")
                .asRuntimeException();

        when(merchantHistoryMapper.filterToGrpc(filter, pageable)).thenReturn(grpcRequest);
        when(historyBlockingStub.getHistory(grpcRequest)).thenThrow(grpcException);

        assertThatThrownBy(() -> service.getHistory(filter, pageable))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");
    }

    @Test
    @DisplayName("Должен выбрасывать BaseException с сообщением 'System connection error' при непредвиденном RuntimeException")
    void shouldThrowBaseException_forGenericException() {
        MerchantHistoryFilter filter = new MerchantHistoryFilter(
                null, null, null, null, null, null, null, null, null
        );
        Pageable pageable = Pageable.unpaged();

        MerchantHistoryRequestGrpc grpcRequest = MerchantHistoryRequestGrpc.newBuilder().build();

        when(merchantHistoryMapper.filterToGrpc(filter, pageable)).thenReturn(grpcRequest);
        when(historyBlockingStub.getHistory(grpcRequest)).thenThrow(new RuntimeException("Unexpected error"));

        assertThatThrownBy(() -> service.getHistory(filter, pageable))
                .isInstanceOf(BaseException.class)
                .hasMessage("System connection error");
    }

    @Test
    @DisplayName("Должен корректно обрабатывать StatusRuntimeException без cause")
    void shouldHandleStatusRuntimeExceptionWithoutCause() {
        MerchantHistoryFilter filter = new MerchantHistoryFilter(
                null, null, null, null, null, null, null, null, null
        );
        Pageable pageable = Pageable.unpaged();

        MerchantHistoryRequestGrpc grpcRequest = MerchantHistoryRequestGrpc.newBuilder().build();
        StatusRuntimeException grpcException = Status.INTERNAL
                .withDescription("Internal error")
                .asRuntimeException();

        when(merchantHistoryMapper.filterToGrpc(filter, pageable)).thenReturn(grpcRequest);
        when(historyBlockingStub.getHistory(grpcRequest)).thenThrow(grpcException);

        assertThatThrownBy(() -> service.getHistory(filter, pageable))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");
    }

}
