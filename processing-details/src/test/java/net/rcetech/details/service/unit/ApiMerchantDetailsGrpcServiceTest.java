package net.rcetech.details.service.unit;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.protobuf.StatusProto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tgb.cryptoexchange.grpc.generated.DetailsGrpc;
import tgb.cryptoexchange.grpc.generated.GetDetailsGrpc;
import tgb.cryptoexchange.grpc.generated.GetDetailsResponseGrpc;
import tgb.cryptoexchange.grpc.generated.MerchantDetailsServiceGrpc;
import net.rcetech.details.dto.ApiDetailsRequestDTO;
import net.rcetech.details.dto.ApiDetailsResponseDTO;
import net.rcetech.details.dto.ClientByApiKeyDTO;
import net.rcetech.details.dto.DetailsDTO;
import net.rcetech.details.enums.RequestMethod;
import net.rcetech.details.exceptions.BaseException;
import net.rcetech.details.exceptions.MerchantDetailsNotFoundException;
import net.rcetech.details.mapper.DetailsMapper;
import net.rcetech.details.service.ApiMerchantDetailsGrpcService;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static net.rcetech.details.constants.Metrics.DETAILS_REQUEST_ERROR;
import static net.rcetech.details.constants.Metrics.DETAILS_REQUEST_NO_DETAILS;

@ExtendWith(MockitoExtension.class)
class ApiMerchantDetailsGrpcServiceTest {

    @Mock
    private MerchantDetailsServiceGrpc.MerchantDetailsServiceFutureStub detailsFutureStub;

    @Mock
    private DetailsMapper detailsMapper;

    @Mock
    private ListenableFuture<GetDetailsResponseGrpc> listenableFuture;

    @InjectMocks
    private ApiMerchantDetailsGrpcService service;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    private UUID requestId;

    private UUID internalId;

    private ClientByApiKeyDTO clientByApiKeyDTO;

    @BeforeEach
    void setUp() {
        requestId = UUID.randomUUID();

        internalId = UUID.randomUUID();

        clientByApiKeyDTO = ClientByApiKeyDTO.builder().clientId(123L).build();
    }

    @Test
    void shouldReturnApiDetailsResponseDTO_whenGrpcCallSucceeds() throws Exception {
        var requestDTO = ApiDetailsRequestDTO.builder()
                .requestId(requestId)
                .internalId(internalId)
                .userId("user-123")
                .amount(1000)
                .methods(Set.of(RequestMethod.SBP, RequestMethod.CARD))
                .build();

        var grpcRequest = GetDetailsGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setInternalId(internalId.toString())
                .setUserId("user-123")
                .setAmount(1000)
                .addRequestMethod("SBP")
                .addRequestMethod("CARD")
                .build();

        var grpcResponse = GetDetailsResponseGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setMerchant("Merchant LLC")
                .setOrderId("order-456")
                .setOrderStatus("PENDING")
                .setAmount(1000)
                .setDetails(DetailsGrpc.newBuilder()
                        .setRequestMethod("SBP")
                        .setDetails("1234567890")
                        .setBank("Test Bank")
                        .setOperator("Operator")
                        .build())
                .build();

        var expectedResponse = ApiDetailsResponseDTO.builder()
                .requestId(requestId.toString())
                .merchant("Merchant LLC")
                .orderId("order-456")
                .orderStatus("PENDING")
                .amount(1000)
                .details(DetailsDTO.builder()
                        .requestMethod("SBP")
                        .details("1234567890")
                        .bank("Test Bank")
                        .operator("Operator")
                        .build())
                .build();

        when(detailsMapper.detailsRequestDTOToGrpc(requestDTO)).thenReturn(grpcRequest);
        when(detailsFutureStub.getDetails(grpcRequest)).thenReturn(listenableFuture);
        when(listenableFuture.get()).thenReturn(grpcResponse);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());
        when(detailsMapper.grpcResponseToDTO(grpcResponse)).thenReturn(expectedResponse);

        var result = service.getDetails(requestDTO, clientByApiKeyDTO);

        assertThat(result)
                .isNotNull()
                .satisfies(dto -> {
                    assertThat(dto.getRequestId()).isEqualTo(requestId.toString());
                    assertThat(dto.getMerchant()).isEqualTo("Merchant LLC");
                    assertThat(dto.getOrderId()).isEqualTo("order-456");
                    assertThat(dto.getOrderStatus()).isEqualTo("PENDING");
                    assertThat(dto.getAmount()).isEqualTo(1000);
                    assertThat(dto.getDetails()).isNotNull();
                    assertThat(dto.getDetails().getRequestMethod()).isEqualTo("SBP");
                    assertThat(dto.getDetails().getDetails()).isEqualTo("1234567890");
                    assertThat(dto.getDetails().getBank()).isEqualTo("Test Bank");
                    assertThat(dto.getDetails().getOperator()).isEqualTo("Operator");
                });

        verify(detailsMapper).detailsRequestDTOToGrpc(requestDTO);
        verify(detailsFutureStub).getDetails(grpcRequest);
        verify(listenableFuture).get();
        verify(listenableFuture).addListener(any(Runnable.class), any());
        verify(detailsMapper).grpcResponseToDTO(grpcResponse);

        verify(meterRegistry, never()).counter(
                eq(DETAILS_REQUEST_NO_DETAILS),
                any(String[].class)
        );
        verify(meterRegistry, never()).counter(
                eq(DETAILS_REQUEST_ERROR),
                any(String[].class)
        );
    }

    @Test
    void shouldHandleRequestWithNullMethods() throws Exception {
        var requestDTO = ApiDetailsRequestDTO.builder()
                .requestId(requestId)
                .internalId(internalId)
                .userId("user-123")
                .amount(500)
                .methods(null)
                .build();

        var grpcRequest = GetDetailsGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setInternalId(internalId.toString())
                .setUserId("user-123")
                .setAmount(500)
                .build();

        var grpcResponse = GetDetailsResponseGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setMerchant("Merchant LLC")
                .setOrderId("order-456")
                .setOrderStatus("PENDING")
                .setAmount(500)
                .setDetails(DetailsGrpc.newBuilder()
                        .setRequestMethod("SBP")
                        .setDetails("1234567890")
                        .build())
                .build();

        var expectedResponse = ApiDetailsResponseDTO.builder()
                .requestId(requestId.toString())
                .merchant("Merchant LLC")
                .orderId("order-456")
                .orderStatus("PENDING")
                .amount(500)
                .details(DetailsDTO.builder()
                        .requestMethod("SBP")
                        .details("1234567890")
                        .build())
                .build();

        when(detailsMapper.detailsRequestDTOToGrpc(requestDTO)).thenReturn(grpcRequest);
        when(detailsFutureStub.getDetails(grpcRequest)).thenReturn(listenableFuture);
        when(listenableFuture.get()).thenReturn(grpcResponse);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());
        when(detailsMapper.grpcResponseToDTO(grpcResponse)).thenReturn(expectedResponse);

        var result = service.getDetails(requestDTO, clientByApiKeyDTO);

        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualTo(500);

        verify(detailsMapper).detailsRequestDTOToGrpc(requestDTO);
        verify(detailsFutureStub).getDetails(grpcRequest);
    }

    @Test
    void shouldHandleRequestWithEmptyMethods() throws Exception {
        var requestDTO = ApiDetailsRequestDTO.builder()
                .requestId(requestId)
                .internalId(internalId)
                .userId("user-123")
                .amount(500)
                .methods(Set.of())
                .build();

        var grpcRequest = GetDetailsGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setInternalId(internalId.toString())
                .setUserId("user-123")
                .setAmount(500)
                .build();

        var grpcResponse = GetDetailsResponseGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setMerchant("Merchant LLC")
                .setOrderId("order-456")
                .setOrderStatus("PENDING")
                .setAmount(500)
                .setDetails(DetailsGrpc.newBuilder()
                        .setRequestMethod("CARD")
                        .setDetails("9876543210")
                        .build())
                .build();

        var expectedResponse = ApiDetailsResponseDTO.builder()
                .requestId(requestId.toString())
                .merchant("Merchant LLC")
                .orderId("order-456")
                .orderStatus("PENDING")
                .amount(500)
                .details(DetailsDTO.builder()
                        .requestMethod("CARD")
                        .details("9876543210")
                        .build())
                .build();

        when(detailsMapper.detailsRequestDTOToGrpc(requestDTO)).thenReturn(grpcRequest);
        when(detailsFutureStub.getDetails(grpcRequest)).thenReturn(listenableFuture);
        when(listenableFuture.get()).thenReturn(grpcResponse);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());
        when(detailsMapper.grpcResponseToDTO(grpcResponse)).thenReturn(expectedResponse);

        var result = service.getDetails(requestDTO, clientByApiKeyDTO);

        assertThat(result).isNotNull();
        assertThat(result.getDetails().getRequestMethod()).isEqualTo("CARD");
    }

    @Test
    void shouldThrowMerchantDetailsNotFoundException_whenGrpcReturnsNotFound() throws Exception {
        var requestDTO = ApiDetailsRequestDTO.builder()
                .requestId(requestId)
                .internalId(internalId)
                .userId("user-123")
                .amount(1000)
                .methods(Set.of(RequestMethod.SBP))
                .build();

        var grpcRequest = GetDetailsGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setInternalId(internalId.toString())
                .setUserId("user-123")
                .setAmount(1000)
                .addRequestMethod("SBP")
                .build();

        var status = Status.newBuilder()
                .setCode(Code.NOT_FOUND_VALUE)
                .setMessage("Merchant details not found")
                .build();

        var grpcException = StatusProto.toStatusRuntimeException(status);

        when(detailsMapper.detailsRequestDTOToGrpc(requestDTO)).thenReturn(grpcRequest);
        when(detailsFutureStub.getDetails(grpcRequest)).thenReturn(listenableFuture);
        when(listenableFuture.get()).thenThrow(new ExecutionException(grpcException));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);

        assertThatThrownBy(() -> service.getDetails(requestDTO, clientByApiKeyDTO))
                .isInstanceOf(MerchantDetailsNotFoundException.class)
                .hasNoCause();

        verify(detailsMapper).detailsRequestDTOToGrpc(requestDTO);
        verify(detailsFutureStub).getDetails(grpcRequest);
        verify(listenableFuture).get();
        verify(listenableFuture).addListener(any(Runnable.class), any());
        verify(detailsMapper, never()).grpcResponseToDTO(any());

        verify(meterRegistry, times(1)).counter(
                eq(DETAILS_REQUEST_NO_DETAILS),
                any(String[].class)
        );
        verify(meterRegistry, never()).counter(
                eq(DETAILS_REQUEST_ERROR),
                any(String[].class)
        );
    }

    @Test
    void shouldThrowMerchantDetailsNotFoundException_whenGrpcReturnsNotFoundWithoutStatus() throws Exception {
        var requestDTO = ApiDetailsRequestDTO.builder()
                .requestId(requestId)
                .internalId(internalId)
                .userId("user-123")
                .amount(1000)
                .methods(Set.of(RequestMethod.CARD))
                .build();

        var grpcRequest = GetDetailsGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setInternalId(internalId.toString())
                .setUserId("user-123")
                .setAmount(1000)
                .addRequestMethod("CARD")
                .build();

        var grpcException = io.grpc.Status.NOT_FOUND
                .withDescription("Not found")
                .asRuntimeException();

        when(detailsMapper.detailsRequestDTOToGrpc(requestDTO)).thenReturn(grpcRequest);
        when(detailsFutureStub.getDetails(grpcRequest)).thenReturn(listenableFuture);
        when(listenableFuture.get()).thenThrow(new ExecutionException(grpcException));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);

        assertThatThrownBy(() -> service.getDetails(requestDTO, clientByApiKeyDTO))
                .isInstanceOf(MerchantDetailsNotFoundException.class)
                .hasNoCause();
        verify(meterRegistry, times(1)).counter(
                eq(DETAILS_REQUEST_NO_DETAILS),
                any(String[].class)
        );
        verify(meterRegistry, never()).counter(
                eq(DETAILS_REQUEST_ERROR),
                any(String[].class)
        );
    }

    @Test
    void shouldThrowBaseException_forGrpcInternalError() throws Exception {
        var requestDTO = ApiDetailsRequestDTO.builder()
                .requestId(requestId)
                .internalId(internalId)
                .userId("user-123")
                .amount(1000)
                .methods(Set.of(RequestMethod.SBP))
                .build();

        var grpcRequest = GetDetailsGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setInternalId(internalId.toString())
                .setUserId("user-123")
                .setAmount(1000)
                .addRequestMethod("SBP")
                .build();

        var grpcException = io.grpc.Status.INTERNAL
                .withDescription("Internal server error")
                .asRuntimeException();

        when(detailsMapper.detailsRequestDTOToGrpc(requestDTO)).thenReturn(grpcRequest);
        when(detailsFutureStub.getDetails(grpcRequest)).thenReturn(listenableFuture);
        when(listenableFuture.get()).thenThrow(new ExecutionException(grpcException));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);

        assertThatThrownBy(() -> service.getDetails(requestDTO, clientByApiKeyDTO))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");
        verify(meterRegistry, never()).counter(
                eq(DETAILS_REQUEST_NO_DETAILS),
                any(String[].class)
        );
        verify(meterRegistry, times(1)).counter(
                eq(DETAILS_REQUEST_ERROR),
                any(String[].class)
        );
    }

    @Test
    void shouldThrowBaseException_forGrpcUnavailableError() throws Exception {
        var requestDTO = ApiDetailsRequestDTO.builder()
                .requestId(requestId)
                .internalId(internalId)
                .userId("user-123")
                .amount(1000)
                .methods(Set.of(RequestMethod.SBP))
                .build();

        var grpcRequest = GetDetailsGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setInternalId(internalId.toString())
                .setUserId("user-123")
                .setAmount(1000)
                .addRequestMethod("SBP")
                .build();

        var grpcException = io.grpc.Status.UNAVAILABLE
                .withDescription("Service unavailable")
                .asRuntimeException();

        when(detailsMapper.detailsRequestDTOToGrpc(requestDTO)).thenReturn(grpcRequest);
        when(detailsFutureStub.getDetails(grpcRequest)).thenReturn(listenableFuture);
        when(listenableFuture.get()).thenThrow(new ExecutionException(grpcException));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);

        assertThatThrownBy(() -> service.getDetails(requestDTO, clientByApiKeyDTO))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");
    }

    @Test
    void shouldThrowBaseException_forGrpcPermissionDeniedError() throws Exception {
        var requestDTO = ApiDetailsRequestDTO.builder()
                .requestId(requestId)
                .internalId(internalId)
                .userId("user-123")
                .amount(1000)
                .methods(Set.of(RequestMethod.SBP))
                .build();

        var grpcRequest = GetDetailsGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setInternalId(internalId.toString())
                .setUserId("user-123")
                .setAmount(1000)
                .addRequestMethod("SBP")
                .build();

        var grpcException = io.grpc.Status.PERMISSION_DENIED
                .withDescription("Access denied")
                .asRuntimeException();

        when(detailsMapper.detailsRequestDTOToGrpc(requestDTO)).thenReturn(grpcRequest);
        when(detailsFutureStub.getDetails(grpcRequest)).thenReturn(listenableFuture);
        when(listenableFuture.get()).thenThrow(new ExecutionException(grpcException));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);

        assertThatThrownBy(() -> service.getDetails(requestDTO, clientByApiKeyDTO))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");
    }

    @Test
    void shouldThrowBaseException_forNetworkErrors() throws Exception {
        var requestDTO = ApiDetailsRequestDTO.builder()
                .requestId(requestId)
                .internalId(internalId)
                .userId("user-123")
                .amount(1000)
                .methods(Set.of(RequestMethod.SBP))
                .build();

        var grpcRequest = GetDetailsGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setInternalId(internalId.toString())
                .setUserId("user-123")
                .setAmount(1000)
                .addRequestMethod("SBP")
                .build();

        var networkException = new RuntimeException("Connection refused");

        when(detailsMapper.detailsRequestDTOToGrpc(requestDTO)).thenReturn(grpcRequest);
        when(detailsFutureStub.getDetails(grpcRequest)).thenReturn(listenableFuture);
        when(listenableFuture.get()).thenThrow(new ExecutionException(networkException));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);

        assertThatThrownBy(() -> service.getDetails(requestDTO, clientByApiKeyDTO))
                .isInstanceOf(BaseException.class)
                .hasMessage("System connection error");
    }

    @Test
    void shouldThrowBaseException_forInterruptedException() throws Exception {
        var requestDTO = ApiDetailsRequestDTO.builder()
                .requestId(requestId)
                .internalId(internalId)
                .userId("user-123")
                .amount(1000)
                .methods(Set.of(RequestMethod.SBP))
                .build();

        var grpcRequest = GetDetailsGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setInternalId(internalId.toString())
                .setUserId("user-123")
                .setAmount(1000)
                .addRequestMethod("SBP")
                .build();

        var interruptedException = new InterruptedException("Thread interrupted");

        when(detailsMapper.detailsRequestDTOToGrpc(requestDTO)).thenReturn(grpcRequest);
        when(detailsFutureStub.getDetails(grpcRequest)).thenReturn(listenableFuture);
        when(listenableFuture.get()).thenThrow(interruptedException);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);

        assertThatThrownBy(() -> service.getDetails(requestDTO, clientByApiKeyDTO))
                .isInstanceOf(BaseException.class)
                .hasMessage("System connection error");

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void shouldThrowBaseException_forGenericException() throws Exception {
        var requestDTO = ApiDetailsRequestDTO.builder()
                .requestId(requestId)
                .internalId(internalId)
                .userId("user-123")
                .amount(1000)
                .methods(Set.of(RequestMethod.SBP))
                .build();

        var grpcRequest = GetDetailsGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setInternalId(internalId.toString())
                .setUserId("user-123")
                .setAmount(1000)
                .addRequestMethod("SBP")
                .build();

        var genericException = new RuntimeException("Unexpected error");

        when(detailsMapper.detailsRequestDTOToGrpc(requestDTO)).thenReturn(grpcRequest);
        when(detailsFutureStub.getDetails(grpcRequest)).thenReturn(listenableFuture);
        when(listenableFuture.get()).thenThrow(genericException);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);

        assertThatThrownBy(() -> service.getDetails(requestDTO, clientByApiKeyDTO))
                .isInstanceOf(BaseException.class)
                .hasMessage("System connection error");
    }

    @Test
    void shouldHandleStatusRuntimeExceptionWithoutCause() throws Exception {
        var requestDTO = ApiDetailsRequestDTO.builder()
                .requestId(requestId)
                .internalId(internalId)
                .userId("user-123")
                .amount(1000)
                .methods(Set.of(RequestMethod.SBP))
                .build();

        var grpcRequest = GetDetailsGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setInternalId(internalId.toString())
                .setUserId("user-123")
                .setAmount(1000)
                .addRequestMethod("SBP")
                .build();

        var grpcException = io.grpc.Status.INTERNAL
                .withDescription("Internal error")
                .asRuntimeException();

        when(detailsMapper.detailsRequestDTOToGrpc(requestDTO)).thenReturn(grpcRequest);
        when(detailsFutureStub.getDetails(grpcRequest)).thenReturn(listenableFuture);
        when(listenableFuture.get()).thenThrow(grpcException);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);

        assertThatThrownBy(() -> service.getDetails(requestDTO, clientByApiKeyDTO))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");
    }

    @Test
    void shouldHandleNullRequestDTO() {
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
        assertThatThrownBy(() -> service.getDetails(null, clientByApiKeyDTO))
                .isInstanceOf(BaseException.class)
                .hasMessage("System connection error");
    }

    @Test
    void shouldHandleMapperReturningNull() throws Exception {
        var requestDTO = ApiDetailsRequestDTO.builder()
                .requestId(requestId)
                .internalId(internalId)
                .userId("user-123")
                .amount(1000)
                .methods(Set.of(RequestMethod.SBP))
                .build();

        var grpcRequest = GetDetailsGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setInternalId(internalId.toString())
                .setUserId("user-123")
                .setAmount(1000)
                .addRequestMethod("SBP")
                .build();

        var grpcResponse = GetDetailsResponseGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setMerchant("Merchant LLC")
                .build();

        when(detailsMapper.detailsRequestDTOToGrpc(requestDTO)).thenReturn(grpcRequest);
        when(detailsFutureStub.getDetails(grpcRequest)).thenReturn(listenableFuture);
        when(listenableFuture.get()).thenReturn(grpcResponse);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());
        when(detailsMapper.grpcResponseToDTO(grpcResponse)).thenReturn(null);

        var result = service.getDetails(requestDTO, clientByApiKeyDTO);

        assertThat(result).isNull();
    }

    @Test
    void shouldCorrectlyBuildGrpcRequestThroughMapper() throws Exception {
        var requestDTO = ApiDetailsRequestDTO.builder()
                .requestId(requestId)
                .internalId(internalId)
                .userId("user-999")
                .amount(1500)
                .methods(Set.of(RequestMethod.SBP, RequestMethod.CARD, RequestMethod.QR))
                .build();

        var expectedGrpcRequest = GetDetailsGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setInternalId(internalId.toString())
                .setUserId("user-999")
                .setAmount(1500)
                .addRequestMethod("SBP")
                .addRequestMethod("CARD")
                .addRequestMethod("QR")
                .build();

        var grpcResponse = GetDetailsResponseGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setMerchant("Merchant LLC")
                .build();

        var expectedResponse = ApiDetailsResponseDTO.builder()
                .requestId(requestId.toString())
                .merchant("Merchant LLC")
                .build();

        when(detailsMapper.detailsRequestDTOToGrpc(requestDTO)).thenReturn(expectedGrpcRequest);
        when(detailsFutureStub.getDetails(expectedGrpcRequest)).thenReturn(listenableFuture);
        when(listenableFuture.get()).thenReturn(grpcResponse);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());
        when(detailsMapper.grpcResponseToDTO(grpcResponse)).thenReturn(expectedResponse);

        service.getDetails(requestDTO, clientByApiKeyDTO);

        var requestCaptor = ArgumentCaptor.forClass(GetDetailsGrpc.class);
        verify(detailsFutureStub).getDetails(requestCaptor.capture());
        var capturedRequest = requestCaptor.getValue();

        assertThat(capturedRequest.getRequestId()).isEqualTo(requestId.toString());
        assertThat(capturedRequest.getInternalId()).isEqualTo(internalId.toString());
        assertThat(capturedRequest.getUserId()).isEqualTo("user-999");
        assertThat(capturedRequest.getAmount()).isEqualTo(1500);
        assertThat(capturedRequest.getRequestMethodList())
                .containsExactly("SBP", "CARD", "QR");
    }

    @Test
    void shouldHandleFullFlowFromRequestToResponse() throws Exception {
        var requestDTO = ApiDetailsRequestDTO.builder()
                .requestId(requestId)
                .internalId(internalId)
                .userId("user-full")
                .amount(2000)
                .methods(Set.of(RequestMethod.SBP))
                .build();

        var grpcRequest = GetDetailsGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setInternalId(internalId.toString())
                .setUserId("user-full")
                .setAmount(2000)
                .addRequestMethod("SBP")
                .build();

        var grpcResponse = GetDetailsResponseGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setMerchant("Full Merchant LLC")
                .setOrderId("order-full-123")
                .setOrderStatus("COMPLETED")
                .setAmount(2000)
                .setDetails(DetailsGrpc.newBuilder()
                        .setRequestMethod("SBP")
                        .setDetails("1234567890")
                        .setBank("Full Bank")
                        .setOperator("Full Operator")
                        .build())
                .build();

        var expectedResponse = ApiDetailsResponseDTO.builder()
                .requestId(requestId.toString())
                .merchant("Full Merchant LLC")
                .orderId("order-full-123")
                .orderStatus("COMPLETED")
                .amount(2000)
                .details(DetailsDTO.builder()
                        .requestMethod("SBP")
                        .details("1234567890")
                        .bank("Full Bank")
                        .operator("Full Operator")
                        .build())
                .build();

        when(detailsMapper.detailsRequestDTOToGrpc(requestDTO)).thenReturn(grpcRequest);
        when(detailsFutureStub.getDetails(grpcRequest)).thenReturn(listenableFuture);
        when(listenableFuture.get()).thenReturn(grpcResponse);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());
        when(detailsMapper.grpcResponseToDTO(grpcResponse)).thenReturn(expectedResponse);

        var result = service.getDetails(requestDTO, clientByApiKeyDTO);

        assertThat(result)
                .isNotNull()
                .isEqualTo(expectedResponse)
                .satisfies(dto -> {
                    assertThat(dto.getRequestId()).isEqualTo(requestId.toString());
                    assertThat(dto.getMerchant()).isEqualTo("Full Merchant LLC");
                    assertThat(dto.getOrderId()).isEqualTo("order-full-123");
                    assertThat(dto.getOrderStatus()).isEqualTo("COMPLETED");
                    assertThat(dto.getAmount()).isEqualTo(2000);
                    assertThat(dto.getDetails()).isNotNull();
                    assertThat(dto.getDetails().getRequestMethod()).isEqualTo("SBP");
                    assertThat(dto.getDetails().getDetails()).isEqualTo("1234567890");
                    assertThat(dto.getDetails().getBank()).isEqualTo("Full Bank");
                    assertThat(dto.getDetails().getOperator()).isEqualTo("Full Operator");
                });

        verify(detailsMapper).detailsRequestDTOToGrpc(requestDTO);
        verify(detailsFutureStub).getDetails(grpcRequest);
        verify(listenableFuture).get();
        verify(listenableFuture).addListener(any(Runnable.class), any());
        verify(detailsMapper).grpcResponseToDTO(grpcResponse);
    }

}