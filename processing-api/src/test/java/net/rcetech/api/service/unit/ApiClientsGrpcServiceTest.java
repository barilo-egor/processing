package net.rcetech.api.service.unit;

import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import net.rcetech.grpc.generated.ClientsServiceGrpc;
import net.rcetech.grpc.generated.GetClientByApiKeyGrpc;
import net.rcetech.grpc.generated.GetClientByApiKeyResponseGrpc;
import net.rcetech.api.enums.ClientStatus;
import net.rcetech.api.exceptions.BaseException;
import net.rcetech.api.exceptions.ClientNotFoundException;
import net.rcetech.api.exceptions.InvalidApiKeyException;
import net.rcetech.api.service.ApiClientsGrpcService;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiClientsGrpcServiceTest {

    @Mock
    private ClientsServiceGrpc.ClientsServiceFutureStub clientsFutureStub;

    @Mock
    private ListenableFuture<GetClientByApiKeyResponseGrpc> listenableFuture;

    @InjectMocks
    private ApiClientsGrpcService service;

    @Test
    void shouldReturnClientByApiKeyDTO_whenGrpcCallSucceeds() throws ExecutionException, InterruptedException {
        var keyHash = "testKeyHash123";
        var response = GetClientByApiKeyResponseGrpc.newBuilder()
                .setUsername("testUser")
                .setSecret("testSecret123")
                .setStatus("ACTIVE")
                .build();

        when(clientsFutureStub.getClientByApiKey(any(GetClientByApiKeyGrpc.class)))
                .thenReturn(listenableFuture);

        when(listenableFuture.get()).thenReturn(response);

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());

        var result = service.getClientByApiKey(keyHash);

        assertThat(result)
                .isNotNull()
                .satisfies(dto -> {
                    assertThat(dto.getUsername()).isEqualTo("testUser");
                    assertThat(dto.getSecret()).isEqualTo("testSecret123");
                    assertThat(dto.getStatus()).isEqualTo(ClientStatus.ACTIVE);
                });

        var requestCaptor = ArgumentCaptor.forClass(GetClientByApiKeyGrpc.class);
        verify(clientsFutureStub).getClientByApiKey(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getApiKey()).isEqualTo(keyHash);
    }

    @Test
    void shouldHandleAllClientStatusesCorrectly() throws ExecutionException, InterruptedException {
        var keyHash = "testKeyHash456";
        var response = GetClientByApiKeyResponseGrpc.newBuilder()
                .setUsername("testUser")
                .setSecret("testSecret456")
                .setStatus("ACTIVE")
                .build();

        when(clientsFutureStub.getClientByApiKey(any(GetClientByApiKeyGrpc.class)))
                .thenReturn(listenableFuture);
        when(listenableFuture.get()).thenReturn(response);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());

        var result = service.getClientByApiKey(keyHash);

        assertThat(result.getStatus()).isEqualTo(ClientStatus.ACTIVE);
    }

    @Test
    void shouldThrowClientNotFoundException_whenGrpcReturnsNotFound() throws Exception {
        var keyHash = "nonExistentKey";
        var grpcException = Status.NOT_FOUND
                .withDescription("Client not found")
                .asRuntimeException();

        when(clientsFutureStub.getClientByApiKey(any(GetClientByApiKeyGrpc.class)))
                .thenReturn(listenableFuture);

        when(listenableFuture.get()).thenThrow(new ExecutionException(grpcException));

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());

        assertThatThrownBy(() -> service.getClientByApiKey(keyHash))
                .isInstanceOf(ClientNotFoundException.class)
                .hasNoCause();
    }

    @Test
    void shouldThrowInvalidApiKeyException_whenGrpcReturnsInvalidArgument() throws Exception {
        var keyHash = "invalidFormat";
        var grpcException = Status.INVALID_ARGUMENT
                .withDescription("Invalid key format")
                .asRuntimeException();

        when(clientsFutureStub.getClientByApiKey(any(GetClientByApiKeyGrpc.class)))
                .thenReturn(listenableFuture);
        when(listenableFuture.get()).thenThrow(new ExecutionException(grpcException));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());

        assertThatThrownBy(() -> service.getClientByApiKey(keyHash))
                .isInstanceOf(InvalidApiKeyException.class)
                .hasNoCause();
    }

    @Test
    void shouldThrowBaseException_forGenericGrpcErrors() throws Exception {
        var keyHash = "testKey";
        var grpcException = Status.INTERNAL
                .withDescription("Internal server error")
                .asRuntimeException();

        when(clientsFutureStub.getClientByApiKey(any(GetClientByApiKeyGrpc.class)))
                .thenReturn(listenableFuture);
        when(listenableFuture.get()).thenThrow(new ExecutionException(grpcException));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());

        assertThatThrownBy(() -> service.getClientByApiKey(keyHash))
                .isInstanceOf(BaseException.class)
                .hasMessage("Error executing gRPC call");
    }

    @Test
    void shouldWrapUnexpectedExceptions_inBaseException() throws Exception {
        var keyHash = "testKey";
        var unexpectedException = new RuntimeException("Unexpected error");

        when(clientsFutureStub.getClientByApiKey(any(GetClientByApiKeyGrpc.class)))
                .thenReturn(listenableFuture);

        when(listenableFuture.get()).thenThrow(new ExecutionException(unexpectedException));

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());

        assertThatThrownBy(() -> service.getClientByApiKey(keyHash))
                .isInstanceOf(BaseException.class)
                .hasMessage("Error executing gRPC call");
    }

    @Test
    void shouldHandleEmptyKeyHash() throws Exception {
        var keyHash = "";
        var response = GetClientByApiKeyResponseGrpc.newBuilder()
                .setUsername("testUser")
                .setSecret("testSecret")
                .setStatus("ACTIVE")
                .build();

        when(clientsFutureStub.getClientByApiKey(any(GetClientByApiKeyGrpc.class)))
                .thenReturn(listenableFuture);
        when(listenableFuture.get()).thenReturn(response);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture).addListener(any(Runnable.class), any());

        var result = service.getClientByApiKey(keyHash);

        assertThat(result).isNotNull();

        var requestCaptor = ArgumentCaptor.forClass(GetClientByApiKeyGrpc.class);
        verify(clientsFutureStub).getClientByApiKey(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getApiKey()).isEmpty();
    }

    @Test
    void shouldHandleNullKeyHash() {
        assertThatThrownBy(() -> service.getClientByApiKey(null))
                .isInstanceOf(NullPointerException.class);
    }

}