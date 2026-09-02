package net.rcetech.support.service;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Int32Value;
import net.rcetech.grpc.generated.*;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.support.dto.MerchantConfigResponseDTO;
import net.rcetech.meta.support.dto.MerchantConfigUpdateDTO;
import net.rcetech.support.mapper.MerchantConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantConfigServiceImplTest {

    @Mock
    private MerchantConfigServiceGrpc.MerchantConfigServiceFutureStub configFutureStub;

    @Mock
    private MerchantConfigMapper merchantConfigMapper;

    @Mock
    private ListenableFuture<FindAllMerchantConfigsResponseGrpc> findAllFuture;

    @Mock
    private ListenableFuture<MerchantConfigResponseGrpc> updateFuture;

    @InjectMocks
    private MerchantConfigServiceImpl service;

    private UUID ownerId;

    private Long configId;

    private Merchant testMerchant;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        configId = 42L;
        testMerchant = Merchant.values()[0];
    }

    @Test
    void shouldReturnMerchantConfigList_whenFindAllSucceeds() throws Exception {
        var grpcRequest = FindAllMerchantConfigsRequestGrpc.newBuilder()
                .setOwnerId(ownerId.toString())
                .build();

        var configGrpc = MerchantConfigResponseGrpc.newBuilder()
                .setId(configId)
                .setIsOn(BoolValue.of(true))
                .setMerchant(testMerchant.name())
                .setMaxAmount(Int32Value.of(10_000))
                .setMinAmount(Int32Value.of(100))
                .build();

        var grpcResponse = FindAllMerchantConfigsResponseGrpc.newBuilder()
                .addConfigs(configGrpc)
                .build();

        var expectedResponse = List.of(
                new MerchantConfigResponseDTO(configId, true, testMerchant, 10_000, 100)
        );

        when(configFutureStub.findAll(grpcRequest)).thenReturn(findAllFuture);
        mockFutureSuccess(findAllFuture, grpcResponse);
        when(merchantConfigMapper.merchantConfigsToList(grpcResponse)).thenReturn(expectedResponse);

        var result = service.findAll(ownerId);

        assertThat(result)
                .isNotNull()
                .hasSize(1)
                .first()
                .satisfies(dto -> {
                    assertThat(dto.id()).isEqualTo(configId);
                    assertThat(dto.isOn()).isTrue();
                    assertThat(dto.merchant()).isEqualTo(testMerchant);
                    assertThat(dto.maxAmount()).isEqualTo(10_000);
                    assertThat(dto.minAmount()).isEqualTo(100);
                });

        verify(configFutureStub).findAll(grpcRequest);
        verify(findAllFuture).get();
        verify(findAllFuture).addListener(any(Runnable.class), any());
        verify(merchantConfigMapper).merchantConfigsToList(grpcResponse);
    }

    @Test
    void shouldPassOwnerIdToGrpcRequest_whenFindAllCalled() throws Exception {
        var grpcResponse = FindAllMerchantConfigsResponseGrpc.newBuilder().build();
        var expectedResponse = List.<MerchantConfigResponseDTO>of();

        when(configFutureStub.findAll(any(FindAllMerchantConfigsRequestGrpc.class))).thenReturn(findAllFuture);
        mockFutureSuccess(findAllFuture, grpcResponse);
        when(merchantConfigMapper.merchantConfigsToList(grpcResponse)).thenReturn(expectedResponse);

        service.findAll(ownerId);

        var requestCaptor = ArgumentCaptor.forClass(FindAllMerchantConfigsRequestGrpc.class);
        verify(configFutureStub).findAll(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getOwnerId()).isEqualTo(ownerId.toString());
    }

    @Test
    void shouldReturnUpdatedConfig_whenUpdateSucceeds() throws Exception {
        var updateDTO = new MerchantConfigUpdateDTO(true, testMerchant, 5_000, 200);

        var grpcRequest = UpdateMerchantConfigRequestGrpc.newBuilder()
                .setId(configId)
                .setIsOn(BoolValue.of(true))
                .setMerchant(testMerchant.name())
                .setMaxAmount(Int32Value.of(5_000))
                .setMinAmount(Int32Value.of(200))
                .build();

        var grpcResponse = MerchantConfigResponseGrpc.newBuilder()
                .setId(configId)
                .setIsOn(BoolValue.of(true))
                .setMerchant(testMerchant.name())
                .setMaxAmount(Int32Value.of(5_000))
                .setMinAmount(Int32Value.of(200))
                .build();

        var expectedResponse = new MerchantConfigResponseDTO(configId, true, testMerchant, 5_000, 200);

        when(merchantConfigMapper.updateDtoToGrpc(configId, updateDTO)).thenReturn(grpcRequest);
        when(configFutureStub.update(grpcRequest)).thenReturn(updateFuture);
        mockFutureSuccess(updateFuture, grpcResponse);
        when(merchantConfigMapper.grpcToDto(grpcResponse)).thenReturn(expectedResponse);

        var result = service.update(configId, updateDTO);

        assertThat(result)
                .isNotNull()
                .isEqualTo(expectedResponse)
                .satisfies(dto -> {
                    assertThat(dto.id()).isEqualTo(configId);
                    assertThat(dto.isOn()).isTrue();
                    assertThat(dto.merchant()).isEqualTo(testMerchant);
                    assertThat(dto.maxAmount()).isEqualTo(5_000);
                    assertThat(dto.minAmount()).isEqualTo(200);
                });

        verify(merchantConfigMapper).updateDtoToGrpc(configId, updateDTO);
        verify(configFutureStub).update(grpcRequest);
        verify(updateFuture).get();
        verify(updateFuture).addListener(any(Runnable.class), any());
        verify(merchantConfigMapper).grpcToDto(grpcResponse);
    }

    @Test
    void shouldCorrectlyBuildGrpcRequestThroughMapper_whenUpdateCalled() throws Exception {
        var updateDTO = new MerchantConfigUpdateDTO(false, testMerchant, 3_000, null);

        var expectedGrpcRequest = UpdateMerchantConfigRequestGrpc.newBuilder()
                .setId(configId)
                .setIsOn(BoolValue.of(false))
                .setMerchant(testMerchant.name())
                .setMaxAmount(Int32Value.of(3_000))
                .build();

        var grpcResponse = MerchantConfigResponseGrpc.newBuilder()
                .setId(configId)
                .build();

        var expectedResponse = new MerchantConfigResponseDTO(configId, false, testMerchant, 3_000, null);

        when(merchantConfigMapper.updateDtoToGrpc(configId, updateDTO)).thenReturn(expectedGrpcRequest);
        when(configFutureStub.update(expectedGrpcRequest)).thenReturn(updateFuture);
        mockFutureSuccess(updateFuture, grpcResponse);
        when(merchantConfigMapper.grpcToDto(grpcResponse)).thenReturn(expectedResponse);

        service.update(configId, updateDTO);

        verify(merchantConfigMapper).updateDtoToGrpc(configId, updateDTO);
        verify(configFutureStub).update(expectedGrpcRequest);
    }

    @Test
    void shouldThrowBaseException_forGrpcInternalError_whenFindAll() throws Exception {
        var grpcRequest = FindAllMerchantConfigsRequestGrpc.newBuilder()
                .setOwnerId(ownerId.toString())
                .build();

        var grpcException = io.grpc.Status.INTERNAL
                .withDescription("Internal server error")
                .asRuntimeException();

        when(configFutureStub.findAll(grpcRequest)).thenReturn(findAllFuture);
        mockFutureFailure(findAllFuture, new ExecutionException(grpcException));

        assertThatThrownBy(() -> service.findAll(ownerId))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");
    }

    @Test
    void shouldThrowBaseException_forGrpcInternalError_whenUpdate() throws Exception {
        var updateDTO = new MerchantConfigUpdateDTO(null, testMerchant, null, null);

        var grpcRequest = UpdateMerchantConfigRequestGrpc.newBuilder()
                .setId(configId)
                .setMerchant(testMerchant.name())
                .build();

        var grpcException = io.grpc.Status.INTERNAL
                .withDescription("Internal server error")
                .asRuntimeException();

        when(merchantConfigMapper.updateDtoToGrpc(configId, updateDTO)).thenReturn(grpcRequest);
        when(configFutureStub.update(grpcRequest)).thenReturn(updateFuture);
        mockFutureFailure(updateFuture, new ExecutionException(grpcException));

        assertThatThrownBy(() -> service.update(configId, updateDTO))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");
    }

    @Test
    void shouldThrowBaseException_forGrpcUnavailableError_whenFindAll() throws Exception {
        var grpcRequest = FindAllMerchantConfigsRequestGrpc.newBuilder()
                .setOwnerId(ownerId.toString())
                .build();

        var grpcException = io.grpc.Status.UNAVAILABLE
                .withDescription("Service unavailable")
                .asRuntimeException();

        when(configFutureStub.findAll(grpcRequest)).thenReturn(findAllFuture);
        mockFutureFailure(findAllFuture, new ExecutionException(grpcException));

        assertThatThrownBy(() -> service.findAll(ownerId))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");
    }

    @Test
    void shouldThrowBaseException_forGrpcPermissionDeniedError_whenUpdate() throws Exception {
        var updateDTO = new MerchantConfigUpdateDTO(true, null, null, null);

        var grpcRequest = UpdateMerchantConfigRequestGrpc.newBuilder()
                .setId(configId)
                .setIsOn(BoolValue.of(true))
                .build();

        var grpcException = io.grpc.Status.PERMISSION_DENIED
                .withDescription("Access denied")
                .asRuntimeException();

        when(merchantConfigMapper.updateDtoToGrpc(configId, updateDTO)).thenReturn(grpcRequest);
        when(configFutureStub.update(grpcRequest)).thenReturn(updateFuture);
        mockFutureFailure(updateFuture, new ExecutionException(grpcException));

        assertThatThrownBy(() -> service.update(configId, updateDTO))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");
    }

    @Test
    void shouldThrowBaseException_forNetworkErrors_whenFindAll() throws Exception {
        var grpcRequest = FindAllMerchantConfigsRequestGrpc.newBuilder()
                .setOwnerId(ownerId.toString())
                .build();

        var networkException = new RuntimeException("Connection refused");

        when(configFutureStub.findAll(grpcRequest)).thenReturn(findAllFuture);
        mockFutureFailure(findAllFuture, new ExecutionException(networkException));

        assertThatThrownBy(() -> service.findAll(ownerId))
                .isInstanceOf(BaseException.class)
                .hasMessage("System connection error");
    }

    @Test
    void shouldThrowBaseException_forNetworkErrors_whenUpdate() throws Exception {
        var updateDTO = new MerchantConfigUpdateDTO(null, null, 1_000, null);

        var grpcRequest = UpdateMerchantConfigRequestGrpc.newBuilder()
                .setId(configId)
                .setMaxAmount(Int32Value.of(1_000))
                .build();

        var networkException = new RuntimeException("Connection refused");

        when(merchantConfigMapper.updateDtoToGrpc(configId, updateDTO)).thenReturn(grpcRequest);
        when(configFutureStub.update(grpcRequest)).thenReturn(updateFuture);
        mockFutureFailure(updateFuture, new ExecutionException(networkException));

        assertThatThrownBy(() -> service.update(configId, updateDTO))
                .isInstanceOf(BaseException.class)
                .hasMessage("System connection error");
    }

    @Test
    void shouldThrowBaseException_forInterruptedException_whenFindAll() throws Exception {
        var grpcRequest = FindAllMerchantConfigsRequestGrpc.newBuilder()
                .setOwnerId(ownerId.toString())
                .build();

        when(configFutureStub.findAll(grpcRequest)).thenReturn(findAllFuture);
        mockFutureFailure(findAllFuture, new InterruptedException("Thread interrupted"));

        assertThatThrownBy(() -> service.findAll(ownerId))
                .isInstanceOf(BaseException.class)
                .hasMessage("System connection error");

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void shouldThrowBaseException_forGenericException_whenUpdate() throws Exception {
        var updateDTO = new MerchantConfigUpdateDTO(null, null, null, 50);

        var grpcRequest = UpdateMerchantConfigRequestGrpc.newBuilder()
                .setId(configId)
                .setMinAmount(Int32Value.of(50))
                .build();

        when(merchantConfigMapper.updateDtoToGrpc(configId, updateDTO)).thenReturn(grpcRequest);
        when(configFutureStub.update(grpcRequest)).thenReturn(updateFuture);
        mockFutureFailure(updateFuture, new RuntimeException("Unexpected error"));

        assertThatThrownBy(() -> service.update(configId, updateDTO))
                .isInstanceOf(BaseException.class)
                .hasMessage("System connection error");
    }

    @Test
    void shouldHandleStatusRuntimeExceptionWithoutCause_whenFindAll() throws Exception {
        var grpcRequest = FindAllMerchantConfigsRequestGrpc.newBuilder()
                .setOwnerId(ownerId.toString())
                .build();

        var grpcException = io.grpc.Status.INTERNAL
                .withDescription("Internal error")
                .asRuntimeException();

        when(configFutureStub.findAll(grpcRequest)).thenReturn(findAllFuture);
        mockFutureFailure(findAllFuture, grpcException);

        assertThatThrownBy(() -> service.findAll(ownerId))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");
    }

    @Test
    void shouldHandleMapperReturningNull_whenUpdate() throws Exception {
        var updateDTO = new MerchantConfigUpdateDTO(true, testMerchant, null, null);

        var grpcRequest = UpdateMerchantConfigRequestGrpc.newBuilder()
                .setId(configId)
                .setIsOn(BoolValue.of(true))
                .setMerchant(testMerchant.name())
                .build();

        var grpcResponse = MerchantConfigResponseGrpc.newBuilder()
                .setId(configId)
                .build();

        when(merchantConfigMapper.updateDtoToGrpc(configId, updateDTO)).thenReturn(grpcRequest);
        when(configFutureStub.update(grpcRequest)).thenReturn(updateFuture);
        mockFutureSuccess(updateFuture, grpcResponse);
        when(merchantConfigMapper.grpcToDto(grpcResponse)).thenReturn(null);

        var result = service.update(configId, updateDTO);

        assertThat(result).isNull();
    }

    @Test
    void shouldHandleFullFlowFromRequestToResponse_whenUpdate() throws Exception {
        var updateDTO = new MerchantConfigUpdateDTO(true, testMerchant, 8_000, 150);

        var grpcRequest = UpdateMerchantConfigRequestGrpc.newBuilder()
                .setId(configId)
                .setIsOn(BoolValue.of(true))
                .setMerchant(testMerchant.name())
                .setMaxAmount(Int32Value.of(8_000))
                .setMinAmount(Int32Value.of(150))
                .build();

        var grpcResponse = MerchantConfigResponseGrpc.newBuilder()
                .setId(configId)
                .setIsOn(BoolValue.of(true))
                .setMerchant(testMerchant.name())
                .setMaxAmount(Int32Value.of(8_000))
                .setMinAmount(Int32Value.of(150))
                .build();

        var expectedResponse = new MerchantConfigResponseDTO(configId, true, testMerchant, 8_000, 150);

        when(merchantConfigMapper.updateDtoToGrpc(configId, updateDTO)).thenReturn(grpcRequest);
        when(configFutureStub.update(grpcRequest)).thenReturn(updateFuture);
        mockFutureSuccess(updateFuture, grpcResponse);
        when(merchantConfigMapper.grpcToDto(grpcResponse)).thenReturn(expectedResponse);

        var result = service.update(configId, updateDTO);

        assertThat(result)
                .isNotNull()
                .isEqualTo(expectedResponse)
                .satisfies(dto -> {
                    assertThat(dto.id()).isEqualTo(configId);
                    assertThat(dto.isOn()).isTrue();
                    assertThat(dto.merchant()).isEqualTo(testMerchant);
                    assertThat(dto.maxAmount()).isEqualTo(8_000);
                    assertThat(dto.minAmount()).isEqualTo(150);
                });

        verify(merchantConfigMapper).updateDtoToGrpc(configId, updateDTO);
        verify(configFutureStub).update(grpcRequest);
        verify(updateFuture).get();
        verify(updateFuture).addListener(any(Runnable.class), any());
        verify(merchantConfigMapper).grpcToDto(grpcResponse);
    }

    private void mockFutureSuccess(ListenableFuture<?> future, Object result) throws Exception {
        when(future.get()).thenReturn(result);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(future).addListener(any(Runnable.class), any());
    }

    private void mockFutureFailure(ListenableFuture<?> future, Throwable throwable) throws Exception {
        when(future.get()).thenThrow(throwable);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(future).addListener(any(Runnable.class), any());
    }

}
