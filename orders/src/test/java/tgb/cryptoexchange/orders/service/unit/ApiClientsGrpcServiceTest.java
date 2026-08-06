package tgb.cryptoexchange.orders.service.unit;

import com.google.common.util.concurrent.SettableFuture;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tgb.cryptoexchange.grpc.generated.*;
import tgb.cryptoexchange.orders.dto.ClientDTO;
import tgb.cryptoexchange.orders.exceptions.BaseException;
import tgb.cryptoexchange.orders.exceptions.UserNotFoundException;
import tgb.cryptoexchange.orders.mapper.ClientMapper;
import tgb.cryptoexchange.orders.service.ApiClientsGrpcService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiClientsGrpcServiceTest {

    @Mock
    private ClientMapper clientMapper;

    @Mock
    private ClientsServiceGrpc.ClientsServiceFutureStub futureStub;

    @InjectMocks
    private ApiClientsGrpcService apiClientsGrpcService;

    @Test
    @DisplayName("Успешный сценарий: gRPC возвращает ответ, маппер преобразует в DTO")
    void getClientById_Success() {
        long clientId = 100L;
        GetClientByIdResponseGrpc responseGrpc = GetClientByIdResponseGrpc.newBuilder()
                .setId(clientId)
                .setUsername("test_user")
                .build();
        ClientDTO expectedDto = new ClientDTO();

        SettableFuture<GetClientByIdResponseGrpc> guavaFuture = SettableFuture.create();
        guavaFuture.set(responseGrpc);

        when(futureStub.getClientById(any(GetClientByIdGrpc.class))).thenReturn(guavaFuture);
        when(clientMapper.clientByResponseToDTO(responseGrpc)).thenReturn(expectedDto);

        Mono<ClientDTO> resultMono = apiClientsGrpcService.getClientById(clientId);

        StepVerifier.create(resultMono)
                .expectNext(expectedDto)
                .verifyComplete();

        verify(futureStub, times(1)).getClientById(any(GetClientByIdGrpc.class));
        verify(clientMapper, times(1)).clientByResponseToDTO(responseGrpc);
    }

    @Test
    @DisplayName("Ошибка NOT_FOUND: gRPC бросает StatusRuntimeException(NOT_FOUND) -> UserNotFoundException")
    void getClientById_NotFound() {
        Long clientId = 404L;
        StatusRuntimeException grpcException = Status.NOT_FOUND
                .withDescription("Client not found in gRPC service")
                .asRuntimeException();

        SettableFuture<GetClientByIdResponseGrpc> guavaFuture = SettableFuture.create();
        guavaFuture.setException(grpcException);

        when(futureStub.getClientById(any(GetClientByIdGrpc.class))).thenReturn(guavaFuture);

        Mono<ClientDTO> resultMono = apiClientsGrpcService.getClientById(clientId);

        StepVerifier.create(resultMono)
                .expectError(UserNotFoundException.class)
                .verify();

        verify(futureStub, times(1)).getClientById(any(GetClientByIdGrpc.class));
        verifyNoInteractions(clientMapper);
    }

    @Test
    @DisplayName("Системная ошибка: gRPC бросает StatusRuntimeException(UNAVAILABLE) -> BaseException")
    void getClientById_GrpcUnavailable() {
        Long clientId = 503L;
        StatusRuntimeException grpcException = Status.UNAVAILABLE
                .withDescription("Connection timeout")
                .asRuntimeException();

        SettableFuture<GetClientByIdResponseGrpc> guavaFuture = SettableFuture.create();
        guavaFuture.setException(grpcException);

        when(futureStub.getClientById(any(GetClientByIdGrpc.class))).thenReturn(guavaFuture);

        Mono<ClientDTO> resultMono = apiClientsGrpcService.getClientById(clientId);

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable -> throwable instanceof BaseException
                        && throwable.getMessage().equals("Ошибка gRPC: Connection timeout"))
                .verify();
    }

    @Test
    @DisplayName("Критическая ошибка: непредвиденное RuntimeException -> BaseException")
    void getClientById_UnexpectedException() {
        Long clientId = 999L;
        NullPointerException unexpectedException = new NullPointerException("NPE stub hardware failure");

        SettableFuture<GetClientByIdResponseGrpc> guavaFuture = SettableFuture.create();
        guavaFuture.setException(unexpectedException);

        when(futureStub.getClientById(any(GetClientByIdGrpc.class))).thenReturn(guavaFuture);

        Mono<ClientDTO> resultMono = apiClientsGrpcService.getClientById(clientId);

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable -> throwable instanceof BaseException
                        && throwable.getMessage().equals("Критическая ошибка gRPC: NPE stub hardware failure"))
                .verify();

    }

    @Test
    @DisplayName("Успешный сценарий: gRPC возвращает подпись")
    void createSignature_Success() {
        Long clientId = 777L;
        String rawData = "some_payload_data";
        String expectedSignature = "signature_hash_string";

        CreateSignatureResponseGrpc responseGrpc = CreateSignatureResponseGrpc.newBuilder()
                .setSignature(expectedSignature)
                .build();

        SettableFuture<CreateSignatureResponseGrpc> guavaFuture = SettableFuture.create();
        guavaFuture.set(responseGrpc);

        when(futureStub.createSignature(any(CreateSignatureGrpc.class))).thenReturn(guavaFuture);

        Mono<String> resultMono = apiClientsGrpcService.createSignature(clientId, rawData);

        StepVerifier.create(resultMono)
                .expectNext(expectedSignature)
                .verifyComplete();

        verify(futureStub, times(1)).createSignature(any(CreateSignatureGrpc.class));
    }

    @Test
    @DisplayName("Ошибка NOT_FOUND при создании подписи -> UserNotFoundException")
    void createSignature_NotFound() {
        Long clientId = 404L;
        String rawData = "data";
        StatusRuntimeException grpcException = Status.NOT_FOUND
                .withDescription("Client profile missing for signing")
                .asRuntimeException();

        SettableFuture<CreateSignatureResponseGrpc> guavaFuture = SettableFuture.create();
        guavaFuture.setException(grpcException);

        when(futureStub.createSignature(any(CreateSignatureGrpc.class))).thenReturn(guavaFuture);

        Mono<String> resultMono = apiClientsGrpcService.createSignature(clientId, rawData);

        StepVerifier.create(resultMono)
                .expectError(UserNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("Системная ошибка gRPC INTERNAL при создании подписи -> BaseException")
    void createSignature_GrpcInternalError() {

        Long clientId = 500L;
        String rawData = "data";
        StatusRuntimeException grpcException = Status.INTERNAL
                .withDescription("Crypto provider error")
                .asRuntimeException();

        SettableFuture<CreateSignatureResponseGrpc> guavaFuture = SettableFuture.create();
        guavaFuture.setException(grpcException);

        when(futureStub.createSignature(any(CreateSignatureGrpc.class))).thenReturn(guavaFuture);

        Mono<String> resultMono = apiClientsGrpcService.createSignature(clientId, rawData);

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable -> throwable instanceof BaseException
                        && throwable.getMessage().equals("Ошибка gRPC: Crypto provider error"))
                .verify();
    }

    @Test
    @DisplayName("Критическая ошибка времени выполнения при создании подписи -> BaseException")
    void createSignature_UnexpectedException() {
        Long clientId = 888L;
        String rawData = "data";
        IllegalArgumentException unexpectedException = new IllegalArgumentException("Illegal state of context channel");

        SettableFuture<CreateSignatureResponseGrpc> guavaFuture = SettableFuture.create();
        guavaFuture.setException(unexpectedException);

        when(futureStub.createSignature(any(CreateSignatureGrpc.class))).thenReturn(guavaFuture);

        Mono<String> resultMono = apiClientsGrpcService.createSignature(clientId, rawData);

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable -> throwable instanceof BaseException
                        && throwable.getMessage()
                        .equals("Критическая ошибка gRPC при создании подписи: Illegal state of context channel"))
                .verify();

    }

}
