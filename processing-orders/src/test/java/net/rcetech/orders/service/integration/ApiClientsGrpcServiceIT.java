package net.rcetech.orders.service.integration;

import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tgb.cryptoexchange.grpc.generated.*;
import net.rcetech.orders.dto.ClientDTO;
import net.rcetech.orders.exceptions.BaseException;
import net.rcetech.orders.exceptions.UserNotFoundException;
import net.rcetech.orders.mapper.ClientMapper;
import net.rcetech.orders.service.ApiClientsGrpcService;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

class ApiClientsGrpcServiceIT extends BaseIntegrationTest {

    @Autowired
    private ApiClientsGrpcService apiClientsGrpcService;

    @MockitoBean
    private ClientsServiceGrpc.ClientsServiceFutureStub mockFutureStub;

    @Autowired
    private ClientMapper clientMapper;

    @BeforeEach
    void resetMock() {
        Mockito.reset(mockFutureStub);
    }

    @Test
    @DisplayName("Успешное получение и маппинг полного профиля клиента")
    void shouldReturnClientDtoWhenGrpcCallSucceeds() {
        long clientId = 42L;
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();

        GetClientByIdResponseGrpc responseGrpc = GetClientByIdResponseGrpc.newBuilder()
                .setId(clientId)
                .setUsername("elite_developer")
                .setApiKeyPreview("abc...xyz")
                .setRegisteredAt(timestamp)
                .setStatus("ACTIVE")
                .setCallbackUrl("https://example.com")
                .setOrderTimeoutSeconds(300)
                .build();

        SettableFuture<GetClientByIdResponseGrpc> guavaFuture = SettableFuture.create();
        guavaFuture.set(responseGrpc);

        doReturn(guavaFuture).when(mockFutureStub).getClientById(any(GetClientByIdGrpc.class));

        Mono<ClientDTO> resultMono = apiClientsGrpcService.getClientById(clientId);

        StepVerifier.create(resultMono)
                .assertNext(dto -> {
                    assertNotNull(dto, "DTO клиента не должно быть null");
                    ClientDTO expectedDto = clientMapper.clientByResponseToDTO(responseGrpc);
                    assertEquals(expectedDto, dto, "Смаппированный DTO не совпадает со значением из ClientMapper");
                })
                .verifyComplete();

        verify(mockFutureStub).getClientById(any(GetClientByIdGrpc.class));
    }

    @Test
    @DisplayName("Трансляция gRPC ошибки NOT_FOUND в UserNotFoundException")
    void shouldThrowUserNotFoundExceptionWhenClientDoesNotExist() {
        Long clientId = 999L;
        StatusRuntimeException grpcException = Status.NOT_FOUND
                .withDescription("Client with id " + clientId + " not found")
                .asRuntimeException();

        SettableFuture<GetClientByIdResponseGrpc> guavaFuture = SettableFuture.create();
        guavaFuture.setException(grpcException);

        doReturn(guavaFuture).when(mockFutureStub).getClientById(any(GetClientByIdGrpc.class));

        Mono<ClientDTO> resultMono = apiClientsGrpcService.getClientById(clientId);

        StepVerifier.create(resultMono)
                .expectError(UserNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("Трансляция ошибок gRPC в BaseException")
    void shouldThrowBaseExceptionOnStandardGrpcErrors() {
        Long clientId = 100L;
        String errorDescription = "Remote gRPC node is down";
        StatusRuntimeException grpcException = Status.UNAVAILABLE
                .withDescription(errorDescription)
                .asRuntimeException();

        SettableFuture<GetClientByIdResponseGrpc> guavaFuture = SettableFuture.create();
        guavaFuture.setException(grpcException);

        doReturn(guavaFuture).when(mockFutureStub).getClientById(any(GetClientByIdGrpc.class));

        Mono<ClientDTO> resultMono = apiClientsGrpcService.getClientById(clientId);

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable -> throwable instanceof BaseException
                        && throwable.getMessage().contains("Ошибка gRPC: " + errorDescription))
                .verify();
    }

    @Test
    @DisplayName("Обработка непредвиденных Runtime-исключений (Дженерик-ошибки)")
    void shouldThrowBaseExceptionOnUnexpectedRuntimeExceptions() {
        Long clientId = 500L;
        RuntimeException unexpectedException = new RuntimeException("Out of memory on channel parsing");

        SettableFuture<GetClientByIdResponseGrpc> guavaFuture = SettableFuture.create();
        guavaFuture.setException(unexpectedException);

        doReturn(guavaFuture).when(mockFutureStub).getClientById(any(GetClientByIdGrpc.class));

        Mono<ClientDTO> resultMono = apiClientsGrpcService.getClientById(clientId);

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable -> throwable instanceof BaseException
                        && throwable.getMessage().contains("Критическая ошибка gRPC:"))
                .verify();
    }

    @Test
    @DisplayName("Успешная генерация цифровой подписи")
    void shouldReturnSignatureStringWhenGrpcCallSucceeds() {
        Long clientId = 777L;
        String payload = "{\"amount\":100,\"currency\":\"USD\"}";
        String expectedSignature = "MEQCID6s7b...signature_hex_value";

        CreateSignatureResponseGrpc responseGrpc = CreateSignatureResponseGrpc.newBuilder()
                .setSignature(expectedSignature)
                .build();

        SettableFuture<CreateSignatureResponseGrpc> guavaFuture = SettableFuture.create();
        guavaFuture.set(responseGrpc);

        doReturn(guavaFuture).when(mockFutureStub).createSignature(any(CreateSignatureGrpc.class));

        Mono<String> resultMono = apiClientsGrpcService.createSignature(clientId, payload);

        StepVerifier.create(resultMono)
                .expectNext(expectedSignature)
                .verifyComplete();

        verify(mockFutureStub).createSignature(any(CreateSignatureGrpc.class));
    }

    @Test
    @DisplayName("Трансляция gRPC ошибки NOT_FOUND при создании подписи")
    void shouldThrowUserNotFoundExceptionOnSignatureGenerationIfClientMissing() {
        Long clientId = 404L;
        String payload = "some_payload";
        StatusRuntimeException grpcException = Status.NOT_FOUND
                .withDescription("Client not registered for sign keys")
                .asRuntimeException();

        SettableFuture<CreateSignatureResponseGrpc> guavaFuture = SettableFuture.create();
        guavaFuture.setException(grpcException);

        doReturn(guavaFuture).when(mockFutureStub).createSignature(any(CreateSignatureGrpc.class));

        Mono<String> resultMono = apiClientsGrpcService.createSignature(clientId, payload);

        StepVerifier.create(resultMono)
                .expectError(UserNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("Трансляция gRPC ошибки при генерации подписи")
    void shouldThrowBaseExceptionOnGrpcInternalErrorDuringSigning() {
        Long clientId = 888L;
        String payload = "payload";
        String desciption = "Crypto HSM hardware error";
        StatusRuntimeException grpcException = Status.INTERNAL
                .withDescription(desciption)
                .asRuntimeException();

        SettableFuture<CreateSignatureResponseGrpc> guavaFuture = SettableFuture.create();
        guavaFuture.setException(grpcException);

        doReturn(guavaFuture).when(mockFutureStub).createSignature(any(CreateSignatureGrpc.class));

        Mono<String> resultMono = apiClientsGrpcService.createSignature(clientId, payload);

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable -> throwable instanceof BaseException
                        && throwable.getMessage().contains("Ошибка gRPC: " + desciption))
                .verify();
    }

    @Test
    @DisplayName("Обработка непредвиденных исключений при создании подписи")
    void shouldThrowBaseExceptionOnUnexpectedErrorDuringSigning() {
        Long clientId = 111L;
        String payload = "payload";
        IllegalArgumentException unexpectedException = new IllegalArgumentException("Invalid internal state");

        SettableFuture<CreateSignatureResponseGrpc> guavaFuture = SettableFuture.create();
        guavaFuture.setException(unexpectedException);

        doReturn(guavaFuture).when(mockFutureStub).createSignature(any(CreateSignatureGrpc.class));

        Mono<String> resultMono = apiClientsGrpcService.createSignature(clientId, payload);

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable -> throwable instanceof BaseException
                        && throwable.getMessage().contains("Критическая ошибка gRPC при создании подписи:"))
                .verify();
    }

}
