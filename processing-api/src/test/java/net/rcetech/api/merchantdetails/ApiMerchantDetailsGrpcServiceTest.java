package net.rcetech.api.merchantdetails;

import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.grpc.generated.ApiDetailsRequestServiceGrpc;
import net.rcetech.grpc.generated.DetailsGrpc;
import net.rcetech.grpc.generated.DetailsRequestGrpc;
import net.rcetech.grpc.generated.DetailsResponseGrpc;
import net.rcetech.meta.incidents.DetailsSearchErrorEvent;
import net.rcetech.meta.orders.RequestMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiMerchantDetailsGrpcServiceTest {

    @Mock
    private ApiDetailsRequestServiceGrpc.ApiDetailsRequestServiceBlockingStub detailsBlockingStub;

    @Spy
    private DetailsMapper detailsMapper = Mappers.getMapper(DetailsMapper.class);

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ApiMerchantDetailsGrpcService apiMerchantDetailsGrpcService;

    @BeforeEach
    void setUp() {
        apiMerchantDetailsGrpcService =  new ApiMerchantDetailsGrpcService(
                detailsMapper, detailsBlockingStub, "processing", eventPublisher
        );
    }


    @ParameterizedTest
    @CsvSource("""
            ALFA_TEAM,SUCCESS,5215,CARD,1234 1234 1234 1234,Сбербанк
            PAYSCROW,PROCESS,2443,SBP,+79851235423,МТС
            """)
    @DisplayName("Метод должен вернуть валидный ответ.")
    void getDetails_shouldReturnResponse(Merchant merchant, String merchantOrderStatus, Integer amount,
                                         RequestMethod requestMethod, String details, String bank) {
        UUID requestId = UUID.randomUUID();
        UUID merchantOrderId = UUID.randomUUID();
        DetailsResponseGrpc detailsResponseGrpc = DetailsResponseGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setMerchant(merchant.name())
                .setOrderId(merchantOrderId.toString())
                .setOrderStatus(merchantOrderStatus)
                .setDetails(DetailsGrpc.newBuilder()
                        .setRequestMethod(requestMethod.name())
                        .setDetails(details)
                        .setBank(bank)
                        .build())
                .setAmount(amount)
                .build();
        when(detailsBlockingStub.detailsRequest(any())).thenReturn(detailsResponseGrpc);
        UUID clientId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID internalId = UUID.randomUUID();
        ApiDetailsResponse actual = apiMerchantDetailsGrpcService.getDetails(clientId, orderId,
                new CreateOrderRequest(
                        internalId.toString(), 5042, Set.of(RequestMethod.CARD),
                        "https://example.com/callback", "534690"
                ), 60
        ).orElseThrow();
        assertAll(
                () -> assertEquals(requestId.toString(), actual.requestId()),
                () -> assertEquals(merchant, actual.merchant()),
                () -> assertEquals(merchantOrderId.toString(), actual.orderId()),
                () -> assertEquals(merchantOrderStatus, actual.orderStatus()),
                () -> assertEquals(amount, actual.amount()),
                () -> assertEquals(requestMethod, actual.details().requestMethod()),
                () -> assertEquals(details, actual.details().details()),
                () -> assertEquals(bank, actual.details().bank())

        );
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 120})
    @DisplayName("Метод должен вернуть пустой Optional без инцидента, если реквизиты не найдены.")
    void getDetails_shouldReturnEmptyWithoutIncidentIfNotFound(int timeoutSeconds) {
        when(detailsBlockingStub.detailsRequest(any())).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                UUID.randomUUID().toString(), 5042, Set.of(RequestMethod.CARD),
                "https://example.com/callback", "534690"
        );
        UUID clientId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        assertTrue(apiMerchantDetailsGrpcService.getDetails(clientId, orderId, createOrderRequest, timeoutSeconds)
                .isEmpty());
        verifyNoInteractions(eventPublisher);
    }

    @ParameterizedTest
    @ValueSource(ints = {
            1, 3, 6, 14
    })
    @DisplayName("Метод должен вернуть пустой Optional и создать инцидент, если статус ответа GRPC не ожидаем, в том числе CANCELLED не по отмене поиска.")
    void getDetails_shouldReturnEmptyAndSaveIncidentIfUnknownStatus(int statusValue) {
        Status status = Status.fromCodeValue(statusValue);
        when(detailsBlockingStub.detailsRequest(any())).thenThrow(new StatusRuntimeException(status));
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                UUID.randomUUID().toString(), 5042, Set.of(RequestMethod.CARD),
                "https://example.com/callback", "534690"
        );
        UUID clientId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        long before = System.currentTimeMillis();
        assertTrue(apiMerchantDetailsGrpcService.getDetails(clientId, orderId, createOrderRequest, 30).isEmpty());
        assertIncidentPublished(orderId, "Неизвестная ошибка GRPC " + statusValue, before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Illegal State", "Connection refused"})
    @DisplayName("Метод должен вернуть пустой Optional и создать инцидент, если произошла непредвиденная ошибка при попытке получить реквизиты.")
    void getDetails_shouldReturnEmptyAndSaveIncidentIfUnknownException(String message) {
        when(detailsBlockingStub.detailsRequest(any())).thenThrow(new IllegalStateException(message));
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                UUID.randomUUID().toString(), 5042, Set.of(RequestMethod.CARD),
                "https://example.com/callback", "534690"
        );
        UUID clientId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        long before = System.currentTimeMillis();
        assertTrue(apiMerchantDetailsGrpcService.getDetails(clientId, orderId, createOrderRequest, 30).isEmpty());
        assertIncidentPublished(orderId, "Непредвиденная ошибка: " + message, before);
    }

    void assertIncidentPublished(UUID orderId, String error, long before) {
        long after = System.currentTimeMillis();
        ArgumentCaptor<DetailsSearchErrorEvent> captor = ArgumentCaptor.forClass(DetailsSearchErrorEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertAll(
                () -> assertEquals(orderId, captor.getValue().orderId()),
                () -> assertEquals(error, captor.getValue().error()),
                () -> assertTrue(captor.getValue().errorTimestamp() >= before
                        && captor.getValue().errorTimestamp() <= after)
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 120})
    @DisplayName("Отмена поиска по ордеру должна отменять gRPC-вызов и возвращать пустой Optional без инцидента.")
    void cancelSearch_shouldCancelGrpcCallWithoutIncident(int timeoutSeconds) throws Exception {
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch callCancelled = new CountDownLatch(1);
        try (InProcessDetails inProcessDetails = new InProcessDetails((request, responseObserver) -> {
            Context.current().addListener(context -> callCancelled.countDown(), Runnable::run);
            requestReceived.countDown();
        })) {
            UUID orderId = UUID.randomUUID();
            AtomicReference<Optional<ApiDetailsResponse>> result = new AtomicReference<>();
            Thread searchThread = Thread.ofVirtual().start(() -> result.set(inProcessDetails.service()
                    .getDetails(UUID.randomUUID(), orderId, createOrderRequest(), timeoutSeconds)));
            assertTrue(requestReceived.await(5, TimeUnit.SECONDS));

            inProcessDetails.service().cancelSearch(orderId);

            assertTrue(searchThread.join(Duration.ofSeconds(5)));
            assertAll(
                    () -> assertTrue(result.get().isEmpty()),
                    () -> assertTrue(callCancelled.await(5, TimeUnit.SECONDS)),
                    () -> assertFalse(searchThread.isInterrupted())
            );
            verifyNoInteractions(eventPublisher);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "ALFA_TEAM,1234 1234 1234 1234",
            "PAYSCROW,+79851235423"
    })
    @DisplayName("Отмена поиска по другому ордеру не должна отменять gRPC-вызов.")
    void cancelSearch_shouldNotCancelSearchOfAnotherOrder(Merchant merchant, String details) throws Exception {
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch respond = new CountDownLatch(1);
        try (InProcessDetails inProcessDetails = new InProcessDetails((request, responseObserver) -> {
            requestReceived.countDown();
            Thread.ofVirtual().start(() -> {
                try {
                    respond.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                responseObserver.onNext(detailsResponse(request, merchant, details));
                responseObserver.onCompleted();
            });
        })) {
            UUID orderId = UUID.randomUUID();
            AtomicReference<Optional<ApiDetailsResponse>> result = new AtomicReference<>();
            Thread searchThread = Thread.ofVirtual().start(() -> result.set(inProcessDetails.service()
                    .getDetails(UUID.randomUUID(), orderId, createOrderRequest(), 30)));
            assertTrue(requestReceived.await(5, TimeUnit.SECONDS));

            inProcessDetails.service().cancelSearch(UUID.randomUUID());
            respond.countDown();

            assertTrue(searchThread.join(Duration.ofSeconds(5)));
            ApiDetailsResponse actual = result.get().orElseThrow();
            assertAll(
                    () -> assertEquals(merchant, actual.merchant()),
                    () -> assertEquals(details, actual.details().details())
            );
            verifyNoInteractions(eventPublisher);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Сбербанк", "МТС"})
    @DisplayName("Отмена поиска по ордеру после завершения поиска не должна бросать исключение.")
    void cancelSearch_shouldDoNothingAfterSearchCompleted(String bank) {
        when(detailsBlockingStub.detailsRequest(any())).thenAnswer(invocation ->
                detailsResponse(invocation.getArgument(0), Merchant.ALFA_TEAM, bank));
        UUID orderId = UUID.randomUUID();
        assertTrue(apiMerchantDetailsGrpcService.getDetails(UUID.randomUUID(), orderId, createOrderRequest(), 30)
                .isPresent());
        assertDoesNotThrow(() -> apiMerchantDetailsGrpcService.cancelSearch(orderId));
    }

    private CreateOrderRequest createOrderRequest() {
        return new CreateOrderRequest(UUID.randomUUID().toString(), 5042, Set.of(RequestMethod.CARD),
                "https://example.com/callback", "534690");
    }

    private DetailsResponseGrpc detailsResponse(DetailsRequestGrpc request, Merchant merchant, String details) {
        return DetailsResponseGrpc.newBuilder()
                .setRequestId(request.getRequestId())
                .setMerchant(merchant.name())
                .setOrderId(UUID.randomUUID().toString())
                .setOrderStatus("NEW")
                .setDetails(DetailsGrpc.newBuilder()
                        .setRequestMethod(RequestMethod.CARD.name())
                        .setDetails(details)
                        .setBank("Сбербанк")
                        .build())
                .build();
    }

    private class InProcessDetails implements AutoCloseable {

        private final Server server;

        private final ManagedChannel channel;

        private final ApiMerchantDetailsGrpcService service;

        InProcessDetails(BiConsumer<DetailsRequestGrpc, StreamObserver<DetailsResponseGrpc>> handler)
                throws IOException {
            String serverName = InProcessServerBuilder.generateName();
            server = InProcessServerBuilder.forName(serverName).directExecutor()
                    .addService(new ApiDetailsRequestServiceGrpc.ApiDetailsRequestServiceImplBase() {
                        @Override
                        public void detailsRequest(DetailsRequestGrpc request,
                                                   StreamObserver<DetailsResponseGrpc> responseObserver) {
                            handler.accept(request, responseObserver);
                        }
                    }).build().start();
            channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
            service = new ApiMerchantDetailsGrpcService(detailsMapper,
                    ApiDetailsRequestServiceGrpc.newBlockingStub(channel), "processing", eventPublisher);
        }

        ApiMerchantDetailsGrpcService service() {
            return service;
        }

        @Override
        public void close() {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }
}
