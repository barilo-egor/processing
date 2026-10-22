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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantConfigServiceImplTest {

    @Mock
    private ApiMerchantConfigServiceGrpc.ApiMerchantConfigServiceBlockingStub configBlockingStub;

    @Mock
    private MerchantConfigMapper merchantConfigMapper;

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
    void shouldReturnMerchantConfigList_whenFindAllSucceeds() {
        var grpcRequest = FindAllApiMerchantConfigsRequestGrpc.newBuilder()
                .setOwnerId(ownerId.toString())
                .build();

        var configGrpc = ApiMerchantConfigItemGrpc.newBuilder()
                .setId(configId)
                .setIsOn(BoolValue.of(true))
                .setMerchant(testMerchant.name())
                .setMaxAmount(Int32Value.of(10_000))
                .setMinAmount(Int32Value.of(100))
                .build();

        var grpcResponse = FindAllApiMerchantConfigsResponseGrpc.newBuilder()
                .addConfigs(configGrpc)
                .build();

        var expectedResponse = List.of(
                new MerchantConfigResponseDTO(configId, true, testMerchant, 10_000, 100, 1)
        );

        when(configBlockingStub.findAll(grpcRequest)).thenReturn(grpcResponse);
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

        verify(configBlockingStub).findAll(grpcRequest);
        verify(merchantConfigMapper).merchantConfigsToList(grpcResponse);
    }

    @Test
    void shouldPassOwnerIdToGrpcRequest_whenFindAllCalled() {
        var grpcResponse = FindAllApiMerchantConfigsResponseGrpc.newBuilder().build();
        var expectedResponse = List.<MerchantConfigResponseDTO>of();

        when(configBlockingStub.findAll(any(FindAllApiMerchantConfigsRequestGrpc.class))).thenReturn(grpcResponse);
        when(merchantConfigMapper.merchantConfigsToList(grpcResponse)).thenReturn(expectedResponse);

        service.findAll(ownerId);

        var requestCaptor = ArgumentCaptor.forClass(FindAllApiMerchantConfigsRequestGrpc.class);
        verify(configBlockingStub).findAll(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getOwnerId()).isEqualTo(ownerId.toString());
    }

    @Test
    void shouldReturnUpdatedConfig_whenUpdateSucceeds() {
        var updateDTO = new MerchantConfigUpdateDTO(true, 5_000, 200, 1);

        var grpcRequest = UpdateApiMerchantConfigItemGrpc.newBuilder()
                .setId(configId)
                .setIsOn(BoolValue.of(true))
                .setMaxAmount(Int32Value.of(5_000))
                .setMinAmount(Int32Value.of(200))
                .build();

        var grpcResponse = ApiMerchantConfigItemGrpc.newBuilder()
                .setId(configId)
                .setIsOn(BoolValue.of(true))
                .setMerchant(testMerchant.name())
                .setMaxAmount(Int32Value.of(5_000))
                .setMinAmount(Int32Value.of(200))
                .build();

        var expectedResponse = new MerchantConfigResponseDTO(configId, true, testMerchant, 5_000, 200, 1);

        when(merchantConfigMapper.updateDtoToGrpc(configId, updateDTO)).thenReturn(grpcRequest);
        when(configBlockingStub.update(grpcRequest)).thenReturn(grpcResponse);
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
        verify(configBlockingStub).update(grpcRequest);
        verify(merchantConfigMapper).grpcToDto(grpcResponse);
    }

    @Test
    void shouldCorrectlyBuildGrpcRequestThroughMapper_whenUpdateCalled() {
        var updateDTO = new MerchantConfigUpdateDTO(false, 3_000, null, 1);

        var expectedGrpcRequest = UpdateApiMerchantConfigItemGrpc.newBuilder()
                .setId(configId)
                .setIsOn(BoolValue.of(false))
                .setMaxAmount(Int32Value.of(3_000))
                .build();

        var grpcResponse = ApiMerchantConfigItemGrpc.newBuilder()
                .setId(configId)
                .build();

        var expectedResponse = new MerchantConfigResponseDTO(configId, false, testMerchant, 3_000, null, 1);

        when(merchantConfigMapper.updateDtoToGrpc(configId, updateDTO)).thenReturn(expectedGrpcRequest);
        when(configBlockingStub.update(expectedGrpcRequest)).thenReturn(grpcResponse);
        when(merchantConfigMapper.grpcToDto(grpcResponse)).thenReturn(expectedResponse);

        service.update(configId, updateDTO);

        verify(merchantConfigMapper).updateDtoToGrpc(configId, updateDTO);
        verify(configBlockingStub).update(expectedGrpcRequest);
        verify(merchantConfigMapper).grpcToDto(grpcResponse);
    }

    @Test
    void shouldThrowBaseException_forGrpcInternalError_whenFindAll() {
        var grpcRequest = FindAllApiMerchantConfigsRequestGrpc.newBuilder()
                .setOwnerId(ownerId.toString())
                .build();

        var grpcException = io.grpc.Status.INTERNAL
                .withDescription("Internal server error")
                .asRuntimeException();

        when(configBlockingStub.findAll(grpcRequest)).thenThrow(grpcException);

        assertThatThrownBy(() -> service.findAll(ownerId))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");
    }

    @Test
    void shouldThrowBaseException_forGrpcInternalError_whenUpdate() {
        var updateDTO = new MerchantConfigUpdateDTO(null, null, null, 1);

        var grpcRequest = UpdateApiMerchantConfigItemGrpc.newBuilder()
                .setId(configId)
                .build();

        var grpcException = io.grpc.Status.INTERNAL
                .withDescription("Internal server error")
                .asRuntimeException();

        when(merchantConfigMapper.updateDtoToGrpc(configId, updateDTO)).thenReturn(grpcRequest);
        when(configBlockingStub.update(grpcRequest)).thenThrow(grpcException);

        assertThatThrownBy(() -> service.update(configId, updateDTO))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");
    }

    @Test
    void shouldThrowBaseException_forGrpcUnavailableError_whenFindAll() {
        var grpcRequest = FindAllApiMerchantConfigsRequestGrpc.newBuilder()
                .setOwnerId(ownerId.toString())
                .build();

        var grpcException = io.grpc.Status.UNAVAILABLE
                .withDescription("Service unavailable")
                .asRuntimeException();

        when(configBlockingStub.findAll(grpcRequest)).thenThrow(grpcException);

        assertThatThrownBy(() -> service.findAll(ownerId))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");
    }

    @Test
    void shouldThrowBaseException_forGrpcPermissionDeniedError_whenUpdate() {
        var updateDTO = new MerchantConfigUpdateDTO(true, null, null, 1);

        var grpcRequest = UpdateApiMerchantConfigItemGrpc.newBuilder()
                .setId(configId)
                .setIsOn(BoolValue.of(true))
                .build();

        var grpcException = io.grpc.Status.PERMISSION_DENIED
                .withDescription("Access denied")
                .asRuntimeException();

        when(merchantConfigMapper.updateDtoToGrpc(configId, updateDTO)).thenReturn(grpcRequest);
        when(configBlockingStub.update(grpcRequest)).thenThrow(grpcException);

        assertThatThrownBy(() -> service.update(configId, updateDTO))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");
    }

    @Test
    void shouldThrowBaseException_forNetworkErrors_whenFindAll() {
        var grpcRequest = FindAllApiMerchantConfigsRequestGrpc.newBuilder()
                .setOwnerId(ownerId.toString())
                .build();

        var networkException = io.grpc.Status.UNAVAILABLE
                .withDescription("Connection refused")
                .withCause(new java.net.ConnectException("Connection refused"))
                .asRuntimeException();

        when(configBlockingStub.findAll(grpcRequest)).thenThrow(networkException);

        assertThatThrownBy(() -> service.findAll(ownerId))
                .isInstanceOf(BaseException.class)
                .hasMessage("System connection error");
    }

    @Test
    void shouldThrowBaseException_forNetworkErrors_whenUpdate() {
        var updateDTO = new MerchantConfigUpdateDTO(null, 1_000, null, 1);

        var grpcRequest = UpdateApiMerchantConfigItemGrpc.newBuilder()
                .setId(configId)
                .setMaxAmount(Int32Value.of(1_000))
                .build();

        var networkException = io.grpc.Status.UNAVAILABLE
                .withDescription("Connection refused")
                .withCause(new java.net.ConnectException("Connection refused"))
                .asRuntimeException();

        when(merchantConfigMapper.updateDtoToGrpc(configId, updateDTO)).thenReturn(grpcRequest);
        when(configBlockingStub.update(grpcRequest)).thenThrow(networkException);

        assertThatThrownBy(() -> service.update(configId, updateDTO))
                .isInstanceOf(BaseException.class)
                .hasMessage("System connection error");
    }

    @Test
    void shouldThrowBaseException_forCancelledCall_whenFindAll() {
        var grpcRequest = FindAllApiMerchantConfigsRequestGrpc.newBuilder()
                .setOwnerId(ownerId.toString())
                .build();

        var grpcException = io.grpc.Status.CANCELLED
                .withDescription("Context was cancelled")
                .asRuntimeException();

        when(configBlockingStub.findAll(grpcRequest)).thenThrow(grpcException);

        assertThatThrownBy(() -> service.findAll(ownerId))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void shouldThrowBaseException_forGenericException_whenUpdate() {
        var updateDTO = new MerchantConfigUpdateDTO(null, null, 50, 1);

        var grpcRequest = UpdateApiMerchantConfigItemGrpc.newBuilder()
                .setId(configId)
                .setMinAmount(Int32Value.of(50))
                .build();

        when(merchantConfigMapper.updateDtoToGrpc(configId, updateDTO)).thenReturn(grpcRequest);
        when(configBlockingStub.update(grpcRequest)).thenThrow(new RuntimeException("Unexpected error"));

        assertThatThrownBy(() -> service.update(configId, updateDTO))
                .isInstanceOf(BaseException.class)
                .hasMessage("System connection error");
    }

    @Test
    void shouldHandleStatusRuntimeExceptionWithoutCause_whenFindAll() {
        var grpcRequest = FindAllApiMerchantConfigsRequestGrpc.newBuilder()
                .setOwnerId(ownerId.toString())
                .build();

        var grpcException = io.grpc.Status.INTERNAL
                .withDescription("Internal error")
                .asRuntimeException();

        when(configBlockingStub.findAll(grpcRequest)).thenThrow(grpcException);

        assertThatThrownBy(() -> service.findAll(ownerId))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");
    }

    @Test
    void shouldHandleMapperReturningNull_whenUpdate() {
        var updateDTO = new MerchantConfigUpdateDTO(true, null, null, 1);

        var grpcRequest = UpdateApiMerchantConfigItemGrpc.newBuilder()
                .setId(configId)
                .setIsOn(BoolValue.of(true))
                .build();

        var grpcResponse = ApiMerchantConfigItemGrpc.newBuilder()
                .setId(configId)
                .build();

        when(merchantConfigMapper.updateDtoToGrpc(configId, updateDTO)).thenReturn(grpcRequest);
        when(configBlockingStub.update(grpcRequest)).thenReturn(grpcResponse);
        when(merchantConfigMapper.grpcToDto(grpcResponse)).thenReturn(null);

        var result = service.update(configId, updateDTO);

        assertThat(result).isNull();
    }

    @Test
    void shouldHandleFullFlowFromRequestToResponse_whenUpdate() {
        var updateDTO = new MerchantConfigUpdateDTO(true, 8_000, 150, 1);

        var grpcRequest = UpdateApiMerchantConfigItemGrpc.newBuilder()
                .setId(configId)
                .setIsOn(BoolValue.of(true))
                .setMaxAmount(Int32Value.of(8_000))
                .setMinAmount(Int32Value.of(150))
                .build();

        var grpcResponse = ApiMerchantConfigItemGrpc.newBuilder()
                .setId(configId)
                .setIsOn(BoolValue.of(true))
                .setMerchant(testMerchant.name())
                .setMaxAmount(Int32Value.of(8_000))
                .setMinAmount(Int32Value.of(150))
                .build();

        var expectedResponse = new MerchantConfigResponseDTO(configId, true, testMerchant, 8_000, 150, 1);

        when(merchantConfigMapper.updateDtoToGrpc(configId, updateDTO)).thenReturn(grpcRequest);
        when(configBlockingStub.update(grpcRequest)).thenReturn(grpcResponse);
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
        verify(configBlockingStub).update(grpcRequest);
        verify(merchantConfigMapper).grpcToDto(grpcResponse);
    }

    private void mockFutureSuccess(ListenableFuture<?> future, Object result) throws Exception {
        doReturn(result).when(future).get();
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(future).addListener(any(Runnable.class), any());
    }

}
