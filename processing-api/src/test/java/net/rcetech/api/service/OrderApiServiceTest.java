package net.rcetech.api.service;

import net.rcetech.api.dto.CreateOrderCommand;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.api.merchantdetails.ApiDetailsResponse;
import net.rcetech.api.merchantdetails.MerchantDetailsReceiver;
import net.rcetech.api.merchantdetails.RandomDetailsReceiver;
import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.domain.test.SharedMySQLContainer;
import net.rcetech.meta.DatabaseConstraint;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.exception.ExceptionMessage;
import net.rcetech.meta.exception.OrderStatusUpdateException;
import net.rcetech.meta.incidents.AsyncDetailsSearchErrorEvent;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChangeSource;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.meta.orders.dto.ClientOrderFilter;
import net.rcetech.meta.orders.dto.ClientOrderSummary;
import net.rcetech.orders.OrderFacade;
import net.rcetech.orders.callback.MerchantCallbackProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import(OrderService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@RecordApplicationEvents
class OrderApiServiceTest {

    @TestConfiguration
    static class OrderApiServiceTestContextConfiguration {
        @Bean
        public ClientMapper clientMapper() {
            return Mappers.getMapper(ClientMapper.class);
        }

        @Bean
        public ClientService clientService(ClientRepository clientRepository, ClientMapper clientMapper) {
            return new ClientService(clientRepository, clientMapper);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SharedMySQLContainer.register(registry);
    }

    @MockitoBean
    private MerchantDetailsReceiver merchantDetailsReceiver;

    @MockitoBean
    private RandomDetailsReceiver randomDetailsReceiver;


    @Autowired
    private ClientService clientService;

    @MockitoSpyBean
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private final Executor virtualTaskExecutor = mock(Executor.class);

    @SuppressWarnings("unchecked")
    private final BiConsumer<Void, Throwable> onSearchCompleted = mock(BiConsumer.class);

    private final MerchantCallbackProcessor merchantCallbackProcessor = mock(MerchantCallbackProcessor.class);

    private OrderApiService orderApiService;

    @BeforeEach
    void setUp() {
        DetailsSearchService detailsSearchService = new DetailsSearchService(merchantDetailsReceiver,
                randomDetailsReceiver, virtualTaskExecutor);
        orderApiService = new OrderApiService(detailsSearchService, clientService, orderService,
                new OrderFacade(orderService), merchantCallbackProcessor, eventPublisher);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a27fc526-017c-440a-b680-0f672983aeaf",
            "ba31f938-7c47-4b95-bef8-546bacff2292"
    })
    @DisplayName("Метод должен бросить исключение, если клиент не найден.")
    void createOrder_shouldThrowBaseExceptionIfClientNotFound(UUID clientId) {
        for (int i = 0; i < 10; i++) {
            clientService.save(getDummyClient());
        }
        CreateOrderRequest request = new CreateOrderRequest(
                null, null, null, null, null
        );
        assertThrows(BaseException.class, () -> orderApiService.createOrder(command(clientId, request, 30, false)));
    }

    @RepeatedTest(value = 2)
    @DisplayName("Метод должен бросить BadRequestException, если в системе уже существует ордер с переданным internalId.")
    void createOrder_shouldThrowBadRequestExceptionIfInternalIdIsNotUnique() {
        Client client = getDummyClient();
        UUID clientId = client.getId();
        Order order = getDummyOrder(client);
        CreateOrderRequest request = new CreateOrderRequest(
                order.getInternalId(), null, null, null, null
        );
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> orderApiService.createOrder(command(clientId, request, 30, false)));
        assertEquals(ExceptionMessage.INTERNAL_ID_NOT_UNIQUE, exception.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
            "internal-race-1,1000",
            "internal-race-2,7300"
    })
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Метод должен бросить DataIntegrityViolationException с ограничением unique_internal_id, если ордер с таким internalId создан параллельно после проверки уникальности.")
    void createOrder_shouldThrowDataIntegrityViolationExceptionIfInternalIdViolatedOnInsert(String internalId, int amount) {
        Client client = getDummyClient();
        Order existing = new Order();
        existing.setId(UUID.randomUUID());
        existing.setCreatedAt(Instant.now());
        existing.setClient(client);
        existing.setInternalId(internalId);
        existing.setStatus(OrderStatus.SEARCHING);
        existing.setAmount(amount);
        orderRepository.save(existing);
        doReturn(false).when(orderService).existsByInternalId(internalId);
        CreateOrderRequest request = new CreateOrderRequest(
                internalId, amount, Set.of(RequestMethod.CARD), null, "1235153208"
        );
        try {
            DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
                    () -> orderApiService.createOrder(command(client.getId(), request, 30, false)));

            assertTrue(NestedExceptionUtils.getMostSpecificCause(exception).getMessage()
                    .contains(DatabaseConstraint.UNIQUE_INTERNAL_ID));
            verifyNoInteractions(merchantDetailsReceiver);
            assertEquals(1, orderRepository.count());
        } finally {
            orderRepository.deleteAll();
            clientRepository.deleteAll();
        }
    }

    ApiDetailsResponse getDummyApiDetailsResponse() {
        return new ApiDetailsResponse(
                UUID.randomUUID().toString(), Merchant.ALFA_TEAM,
                UUID.randomUUID().toString(), "SUCCESS",
                null, new ApiDetailsResponse.Details(RequestMethod.CARD, "1234 1234 1234 1234", "T-BANK")
        );
    }

    CreateOrderRequest getDummyCreateOrderRequest() {
        return new CreateOrderRequest(
                UUID.randomUUID().toString(), 1000, Set.of(RequestMethod.CARD),
                null, "1235153208"
        );
    }

    CreateOrderCommand command(UUID clientId, CreateOrderRequest request, int timeoutSeconds, boolean testOrder) {
        return new CreateOrderCommand(clientId, request, timeoutSeconds, testOrder);
    }

    Client getDummyClient() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test" + client.getId());
        client.setRegisteredAt(Instant.now());
        return clientService.save(client);
    }

    @ParameterizedTest
    @ValueSource(ints = {
            800, 1500
    })
    @DisplayName("Метод должен создать ордер, срок которого истекает через время, установленное клиенту.")
    void createOrder_shouldCreateOrderWithExpiresAtWithClientOrderTimeout(int orderTimeout) {
        ApiDetailsResponse detailsResponse = getDummyApiDetailsResponse();
        when(merchantDetailsReceiver.getDetails(any(), any(), any(), eq(30)))
                .thenReturn(Optional.of(detailsResponse));
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test");
        client.setRegisteredAt(Instant.now());
        client.setOrderTimeoutSeconds(orderTimeout);
        clientService.save(client);
        Instant time = Instant.now();
        try (MockedStatic<Instant> instantMock = Mockito.mockStatic(Instant.class)) {
            instantMock.when(Instant::now).thenReturn(time);
            orderApiService.createOrder(command(client.getId(), getDummyCreateOrderRequest(), 30, false));
            List<Order> orders = orderRepository.findAll();
            assertEquals(1, orders.size());
            Order order = orders.getFirst();
            assertEquals(time, order.getCreatedAt());
            assertEquals(time.plusSeconds(orderTimeout), order.getExpiresAt());
        }
    }

    @MethodSource("createOrderRequestArguments")
    @ParameterizedTest
    @DisplayName("Метод должен создать ордер с переданными в запросе данными.")
    void createOrder_shouldCreateOrderWithCreateOrderRequestData(CreateOrderRequest createOrderRequest) {
        ApiDetailsResponse detailsResponse = getDummyApiDetailsResponse();
        when(merchantDetailsReceiver.getDetails(any(), any(), any(), eq(30)))
                .thenReturn(Optional.of(detailsResponse));
        Client client = getDummyClient();
        clientService.save(client);
        orderApiService.createOrder(command(client.getId(), createOrderRequest, 30, false));
        List<Order> orders = orderRepository.findAll();
        assertEquals(1, orders.size());
        Order order = orders.getFirst();
        assertAll(
                () -> assertEquals(createOrderRequest.internalId(), order.getInternalId()),
                () -> assertEquals(createOrderRequest.amount(), order.getAmount()),
                () -> assertEquals(createOrderRequest.callbackUrl(), order.getCallbackUrl())
        );
    }

    static Stream<Arguments> createOrderRequestArguments() {
        return Stream.of(
                Arguments.of(
                        new CreateOrderRequest(
                                UUID.randomUUID().toString(), 1000, Set.of(RequestMethod.CARD),
                                "https://example.com/callback", "1235153208"
                        )
                ),
                Arguments.of(
                        new CreateOrderRequest(
                                UUID.randomUUID().toString(), 5452, Set.of(RequestMethod.CARD, RequestMethod.SBP),
                                "https://google.com/callback", UUID.randomUUID().toString()
                        )
                )
        );
    }

    @MethodSource("detailsResponseArguments")
    @ParameterizedTest
    @DisplayName("Метод должен создать ордер с данными, полученными в ответе на запрос реквизитов у мерчантов.")
    void createOrder_shouldCreateOrderWithDetailsResponseData(ApiDetailsResponse apiDetailsResponse) {
        Client client = getDummyClient();
        clientService.save(client);
        when(merchantDetailsReceiver.getDetails(any(), any(), any(), eq(30)))
                .thenReturn(Optional.of(apiDetailsResponse));
        orderApiService.createOrder(command(client.getId(), getDummyCreateOrderRequest(), 30, false));
        List<Order> orders = orderRepository.findAll();
        assertEquals(1, orders.size());
        Order order = orders.getFirst();
        assertAll(
                () -> assertEquals(apiDetailsResponse.amount(), order.getAmount()),
                () -> assertEquals(apiDetailsResponse.merchant(), order.getMerchant()),
                () -> assertEquals(apiDetailsResponse.orderId(), order.getMerchantOrderId()),
                () -> assertEquals(apiDetailsResponse.orderStatus(), order.getMerchantOrderStatus())
        );
    }

    static Stream<Arguments> detailsResponseArguments() {
        return Stream.of(
                Arguments.of(
                        new ApiDetailsResponse(
                                UUID.randomUUID().toString(), Merchant.ALFA_TEAM,
                                UUID.randomUUID().toString(), "SUCCESS",
                                500, new ApiDetailsResponse.Details(RequestMethod.CARD, "1234 1234 1234 1234", "T-BANK")
                        )
                ),
                Arguments.of(
                        new ApiDetailsResponse(
                                UUID.randomUUID().toString(), Merchant.STORM_TRADE,
                                UUID.randomUUID().toString(), "PROCESS",
                                500, new ApiDetailsResponse.Details(RequestMethod.SBP, "+79825672341", "Альфа-банк")
                        )
                )
        );
    }

    @MethodSource("detailsResponseArguments")
    @ParameterizedTest
    @DisplayName("Тестовый ордер должен создаваться со сгенерированными реквизитами без запроса в merchant-details.")
    void createOrder_shouldCreateTestOrderWithGeneratedDetails(ApiDetailsResponse apiDetailsResponse) {
        Client client = getDummyClient();
        CreateOrderRequest createOrderRequest = getDummyCreateOrderRequest();
        when(randomDetailsReceiver.getDetails(any(), any(), eq(createOrderRequest), any()))
                .thenReturn(Optional.of(apiDetailsResponse));
        orderApiService.createOrder(command(client.getId(), createOrderRequest, 30, true));
        List<Order> orders = orderRepository.findAll();
        assertEquals(1, orders.size());
        Order order = orders.getFirst();
        assertAll(
                () -> assertEquals(apiDetailsResponse.merchant(), order.getMerchant()),
                () -> assertEquals(apiDetailsResponse.orderId(), order.getMerchantOrderId()),
                () -> assertEquals(apiDetailsResponse.orderStatus(), order.getMerchantOrderStatus()),
                () -> assertEquals(apiDetailsResponse.details().requestMethod(), order.getMethod()),
                () -> assertEquals(apiDetailsResponse.details().details(), order.getDetails()),
                () -> assertEquals(apiDetailsResponse.details().bank(), order.getBank())
        );
        verifyNoInteractions(merchantDetailsReceiver);
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 60})
    @DisplayName("Обычный ордер должен создаваться с реквизитами из merchant-details без генерации тестовых.")
    void createOrder_shouldNotGenerateDetailsForNotTestOrder(int timeoutSeconds) {
        Client client = getDummyClient();
        when(merchantDetailsReceiver.getDetails(any(), any(), any(), eq(timeoutSeconds)))
                .thenReturn(Optional.of(getDummyApiDetailsResponse()));
        orderApiService.createOrder(command(client.getId(), getDummyCreateOrderRequest(), timeoutSeconds, false));
        verify(merchantDetailsReceiver).getDetails(eq(client.getId()), any(), any(), eq(timeoutSeconds));
        verifyNoInteractions(randomDetailsReceiver);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/callback",
            "https://google.com/merchant-details/callback"
    })
    @DisplayName("Метод должен создать ордер с callback url сохраненным за клиентом.")
    void createOrder_shouldCreateOrderWithClientCallbackUrl(String url) {
        ApiDetailsResponse apiDetailsResponse = getDummyApiDetailsResponse();
        when(merchantDetailsReceiver.getDetails(any(), any(), any(), eq(30)))
                .thenReturn(Optional.of(apiDetailsResponse));
        Client client = getDummyClient();
        client.setCallbackUrl(url);
        orderApiService.createOrder(command(client.getId(), getDummyCreateOrderRequest(), 30, false));
        List<Order> orders = orderRepository.findAll();
        assertEquals(1, orders.size());
        assertEquals(url, orders.getFirst().getCallbackUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "cf7754d2-6613-4140-af3f-7ad6224be68c",
            "494e618a-757a-4a28-a2d1-3e3feb0bde62"
    })
    @DisplayName("Метод должен бросить исключение, если ордер не найден.")
    void cancelOrder_shouldThrowBadRequestExceptionIfOrderNotFound(UUID id) {
        UUID orderId = UUID.randomUUID();
        assertThrows(BadRequestException.class, () -> orderApiService.cancelOrder(orderId, id), "Ордер не найден.");
    }

    Order getDummyOrder(Client client) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCreatedAt(Instant.now());
        order.setExpiresAt(Instant.now().plusSeconds(900));
        order.setClient(client);
        order.setInternalId(UUID.randomUUID().toString());
        order.setStatus(OrderStatus.NEW);
        order.setAmount(5000);
        order.setMerchant(Merchant.ALFA_TEAM);
        order.setMerchantOrderId(UUID.randomUUID().toString());
        order.setMerchantOrderStatus("SUCCESS");
        order.setMethod(RequestMethod.CARD);
        order.setDetails("1234 1234 1234 1234");
        order.setBank("ALFA");
        order.setCallbackUrl("https://google.com/callback");
        return orderRepository.save(order);
    }

    @DisplayName("Метод должен бросить исключение, если ордер не клиента.")
    @RepeatedTest(value = 2)
    void cancelOrder_shouldThrowBadRequestExceptionIfOrderStatusNotFound() {
        Client client = getDummyClient();
        Order order = getDummyOrder(client);
        UUID orderId = order.getId();
        UUID notOwnerClientId = UUID.randomUUID();
        assertThrows(BadRequestException.class, () -> orderApiService.cancelOrder(notOwnerClientId, orderId),
                "Ордер не найден.");
    }

    @RepeatedTest(value = 2)
    @DisplayName("Должен быть отменен только указанный ордер.")
    void cancelOrder_shouldUpdateStatusToCancelled() {
        Client targetClient = getDummyClient();
        Order targetOrder = getDummyOrder(targetClient);
        for (int i = 0; i < 10; i++) {
            getDummyOrder(targetClient);
        }
        Client anotherClient = getDummyClient();
        for (int i = 0; i < 5; i++) {
            getDummyOrder(anotherClient);
        }
        orderApiService.cancelOrder(targetClient.getId(), targetOrder.getId());
        List<Order> orders = orderRepository.findAll();
        for (Order order : orders) {
            assertNotNull(order.getId());
            if (order.getId().equals(targetOrder.getId())) {
                assertEquals(OrderStatus.CANCELED, order.getStatus());
            } else {
                assertEquals(OrderStatus.NEW, order.getStatus());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"NEW", "DISPUTE"})
    @DisplayName("После отмены ордера должно быть опубликовано событие смены статуса с источником CLIENT и КБ клиенту.")
    void cancelOrder_shouldPublishStatusUpdatedEventFromClient(OrderStatus status) {
        Client client = getDummyClient();
        Order order = getDummyOrder(client);
        order.setStatus(status);
        orderRepository.save(order);
        orderApiService.cancelOrder(client.getId(), order.getId());
        assertEquals(List.of(new OrderStatusUpdatedEvent(order.getId(), OrderStatus.CANCELED,
                        OrderStatusChangeSource.CLIENT, true)),
                applicationEvents.stream(OrderStatusUpdatedEvent.class).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0f0d5d0c-2a44-4c31-9d43-5d3b4c9f6a11",
            "7b8e2c61-41b5-4c0e-8f7a-2e9d6c3a1b54"
    })
    @DisplayName("Событие смены статуса не должно публиковаться, если ордер не найден у клиента.")
    void cancelOrder_shouldNotPublishEventIfOrderNotFound(UUID orderId) {
        Client client = getDummyClient();
        getDummyOrder(client);
        UUID clientId = client.getId();
        assertThrows(BadRequestException.class, () -> orderApiService.cancelOrder(clientId, orderId));
        assertEquals(0, applicationEvents.stream(OrderStatusUpdatedEvent.class).count());
    }

    @Test
    @DisplayName("Метод должен вернуть только ордера клиента, совершающего запрос.")
    void findAll_shouldReturnOnlyTargetClientOrders() {
        Client targetClient = getDummyClient();
        List<Order> expectedOrders = List.of(getDummyOrder(targetClient), getDummyOrder(targetClient));
        Client anotherClient = getDummyClient();
        getDummyOrder(anotherClient);

        Page<ClientOrderSummary> actual = orderApiService.findAll(
                targetClient.getId(),
                new ClientOrderFilter(null, null), Pageable.ofSize(10)
        );

        assertEquals(
                expectedOrders.stream().map(Order::getId).collect(Collectors.toSet()),
                actual.getContent().stream().map(ClientOrderSummary::id).collect(Collectors.toSet())
        );
    }

    @ValueSource(strings = {"foo", "merchant", "merchantOrderId", "expiresAt", "client"})
    @ParameterizedTest
    @DisplayName("Метод должен отклонить сортировку по полю, которого нет в ответе клиенту.")
    void findAll_shouldRejectUnsupportedSortField(String field) {
        Client client = getDummyClient();
        getDummyOrder(client);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> orderApiService.findAll(
                client.getId(), new ClientOrderFilter(null, null), PageRequest.of(0, 10, Sort.by("createdAt", field))
        ));

        assertEquals("Сортировка по полю «" + field + "» не поддерживается.", exception.getMessage());
    }

    @ValueSource(strings = {"id", "createdAt", "internalId", "status", "amount", "method", "details", "bank",
            "callbackUrl"})
    @ParameterizedTest
    @DisplayName("Метод должен сортировать по любому полю ответа клиенту.")
    void findAll_shouldSortBySupportedField(String field) {
        Client client = getDummyClient();
        Set<UUID> expected = Set.of(getDummyOrder(client).getId(), getDummyOrder(client).getId());

        Page<ClientOrderSummary> actual = orderApiService.findAll(
                client.getId(), new ClientOrderFilter(null, null), PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, field))
        );

        assertEquals(expected, actual.getContent().stream().map(ClientOrderSummary::id).collect(Collectors.toSet()));
    }

    static Stream<Arguments> findAll_shouldFilterOrdersByStatus() {
        return Stream.of(
                Arguments.of(Set.of(OrderStatus.TIMEOUT)),
                Arguments.of(Set.of(OrderStatus.SUCCESS, OrderStatus.CANCELED))
        );
    }

    @MethodSource
    @ParameterizedTest
    @DisplayName("Метод должен вернуть ордера в любом из статусов фильтра.")
    void findAll_shouldFilterOrdersByStatus(Set<OrderStatus> statuses) {
        Client client = getDummyClient();
        Order nonTargetOrder = getDummyOrder(client);
        assertFalse(statuses.contains(nonTargetOrder.getStatus()));
        Set<UUID> expected = statuses.stream().map(status -> {
            Order order = getDummyOrder(client);
            order.setStatus(status);
            return order.getId();
        }).collect(Collectors.toSet());

        Page<ClientOrderSummary> actual = orderApiService.findAll(
                client.getId(),
                new ClientOrderFilter(statuses, null), Pageable.ofSize(10)
        );

        assertEquals(expected, actual.getContent().stream().map(ClientOrderSummary::id).collect(Collectors.toSet()));
    }

    static Stream<Arguments> findAll_shouldFilterOrdersByMethod() {
        return Stream.of(
                Arguments.of(Set.of(RequestMethod.SBP), 1),
                Arguments.of(Set.of(RequestMethod.SBP, RequestMethod.CARD), 2)
        );
    }

    @MethodSource
    @ParameterizedTest
    @DisplayName("Метод должен вернуть ордера с любым из методов фильтра.")
    void findAll_shouldFilterOrdersByMethod(Set<RequestMethod> methods, int expectedCount) {
        Client client = getDummyClient();
        Order cardOrder = getDummyOrder(client);
        cardOrder.setMethod(RequestMethod.CARD);
        Order sbpOrder = getDummyOrder(client);
        sbpOrder.setMethod(RequestMethod.SBP);

        Page<ClientOrderSummary> actual = orderApiService.findAll(
                client.getId(),
                new ClientOrderFilter(null, methods), Pageable.ofSize(10)
        );

        assertEquals(expectedCount, actual.getContent().size());
        assertTrue(actual.getContent().stream().allMatch(order -> methods.contains(order.method())));
    }


    @ParameterizedTest
    @ValueSource(ints = {1000, 7300})
    @DisplayName("Во время поиска реквизитов ордер должен быть сохранён в статусе SEARCHING без реквизитов.")
    void createOrder_shouldSaveSearchingOrderBeforeDetailsRequest(int amount) {
        Client client = getDummyClient();
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID().toString(), amount, Set.of(RequestMethod.CARD), null, "1235153208"
        );
        when(merchantDetailsReceiver.getDetails(any(), any(), any(), eq(30))).thenAnswer(invocation -> {
            List<Order> orders = orderRepository.findAll();
            assertEquals(1, orders.size());
            Order order = orders.getFirst();
            assertAll(
                    () -> assertEquals(invocation.getArgument(1, UUID.class), order.getId()),
                    () -> assertEquals(OrderStatus.SEARCHING, order.getStatus()),
                    () -> assertEquals(request.internalId(), order.getInternalId()),
                    () -> assertEquals(amount, order.getAmount()),
                    () -> assertNull(order.getExpiresAt()),
                    () -> assertNull(order.getMerchant()),
                    () -> assertNull(order.getDetails())
            );
            return Optional.of(getDummyApiDetailsResponse());
        });

        orderApiService.createOrder(command(client.getId(), request, 30, false));

        verify(merchantDetailsReceiver).getDetails(any(), any(), any(), eq(30));
    }

    @MethodSource("detailsResponseArguments")
    @ParameterizedTest
    @DisplayName("После нахождения реквизитов ордер должен перейти в NEW без отправки КБ клиенту.")
    void createOrder_shouldMoveOrderToNewWithoutClientCallback(ApiDetailsResponse apiDetailsResponse) {
        Client client = getDummyClient();
        when(merchantDetailsReceiver.getDetails(any(), any(), any(), eq(30)))
                .thenReturn(Optional.of(apiDetailsResponse));

        ClientOrderSummary order = orderApiService.createOrder(
                command(client.getId(), getDummyCreateOrderRequest(), 30, false));

        assertAll(
                () -> assertEquals(OrderStatus.NEW, order.status()),
                () -> assertEquals(apiDetailsResponse.details().details(), order.details()),
                () -> assertEquals(apiDetailsResponse.details().bank(), order.bank()),
                () -> assertEquals(apiDetailsResponse.details().requestMethod(), order.method()),
                () -> assertNotNull(orderRepository.findById(order.id()).orElseThrow().getExpiresAt())
        );
        assertEquals(List.of(new OrderStatusUpdatedEvent(order.id(), OrderStatus.NEW,
                        OrderStatusChangeSource.SYSTEM, false)),
                applicationEvents.stream(OrderStatusUpdatedEvent.class).toList());
        verify(merchantCallbackProcessor).process(order.id());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Connection is not available", "Deadlock found"})
    @DisplayName("Ошибка обработки КБ мерчанта после нахождения реквизитов не должна ломать создание ордера.")
    void createOrder_shouldReturnNewOrderIfMerchantCallbacksProcessingFailed(String message) {
        Client client = getDummyClient();
        ApiDetailsResponse apiDetailsResponse = (ApiDetailsResponse) detailsResponseArguments().findFirst()
                .orElseThrow().get()[0];
        when(merchantDetailsReceiver.getDetails(any(), any(), any(), eq(30)))
                .thenReturn(Optional.of(apiDetailsResponse));
        doThrow(new IllegalStateException(message)).when(merchantCallbackProcessor).process(any());

        ClientOrderSummary order = orderApiService.createOrder(
                command(client.getId(), getDummyCreateOrderRequest(), 30, false));

        assertEquals(OrderStatus.NEW, order.status());
        verify(merchantCallbackProcessor).process(order.id());
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 60})
    @DisplayName("Если реквизиты не найдены, метод должен вернуть ордер в DETAILS_NOT_FOUND без КБ клиенту.")
    void createOrder_shouldReturnDetailsNotFoundOrderIfDetailsNotFound(int timeoutSeconds) {
        Client client = getDummyClient();
        when(merchantDetailsReceiver.getDetails(any(), any(), any(), eq(timeoutSeconds)))
                .thenReturn(Optional.empty());

        ClientOrderSummary order = orderApiService.createOrder(command(client.getId(), getDummyCreateOrderRequest(),
                timeoutSeconds, false));

        assertEquals(OrderStatus.DETAILS_NOT_FOUND, order.status());
        assertNull(order.details());
        assertEquals(1, orderRepository.count());
        assertEquals(List.of(new OrderStatusUpdatedEvent(order.id(), OrderStatus.DETAILS_NOT_FOUND,
                        OrderStatusChangeSource.SYSTEM, false)),
                applicationEvents.stream(OrderStatusUpdatedEvent.class).toList());
        verify(merchantCallbackProcessor, never()).process(any());
    }

    @MethodSource("detailsResponseArguments")
    @ParameterizedTest
    @DisplayName("Если ордер отменили во время поиска, метод должен вернуть отменённый ордер без реквизитов.")
    void createOrder_shouldReturnCanceledOrderIfCanceledDuringSearch(ApiDetailsResponse apiDetailsResponse) {
        Client client = getDummyClient();
        when(merchantDetailsReceiver.getDetails(any(), any(), any(), eq(30))).thenAnswer(invocation -> {
            orderRepository.findAll().getFirst().setStatus(OrderStatus.CANCELED);
            return Optional.of(apiDetailsResponse);
        });

        ClientOrderSummary order = orderApiService.createOrder(
                command(client.getId(), getDummyCreateOrderRequest(), 30, false));

        assertEquals(OrderStatus.CANCELED, order.status());
        assertNull(order.details());
        assertEquals(0, applicationEvents.stream(OrderStatusUpdatedEvent.class).count());
        verify(merchantCallbackProcessor, never()).process(any());
    }

    @ParameterizedTest
    @CsvSource({
            "CANCELED,Ордер уже в статусе «Отменен».",
            "TIMEOUT,Переход ордера из статуса «Просрочен» в статус «Отменен» не выполняется.",
            "DETAILS_NOT_FOUND,Переход ордера из статуса «Реквизиты не найдены» в статус «Отменен» не выполняется."
    })
    @DisplayName("Метод должен бросить OrderStatusUpdateException без события смены статуса, если переход в CANCELED пропускается.")
    void cancelOrder_shouldThrowIfTransitionSkipped(OrderStatus status, String expectedMessage) {
        Client client = getDummyClient();
        Order order = getDummyOrder(client);
        order.setStatus(status);
        UUID clientId = client.getId();
        UUID orderId = order.getId();

        OrderStatusUpdateException exception = assertThrows(OrderStatusUpdateException.class,
                () -> orderApiService.cancelOrder(clientId, orderId));

        assertEquals(expectedMessage, exception.getMessage());
        assertEquals(status, orderRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(0, applicationEvents.stream(OrderStatusUpdatedEvent.class).count());
    }

    @RepeatedTest(value = 2)
    @DisplayName("Метод должен бросить OrderStatusUpdateException без события смены статуса, если ордер успешный.")
    void cancelOrder_shouldThrowIfOrderSuccess() {
        Client client = getDummyClient();
        Order order = getDummyOrder(client);
        order.setStatus(OrderStatus.SUCCESS);
        UUID clientId = client.getId();
        UUID orderId = order.getId();

        assertThrows(OrderStatusUpdateException.class, () -> orderApiService.cancelOrder(clientId, orderId));

        assertEquals(OrderStatus.SUCCESS, orderRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(0, applicationEvents.stream(OrderStatusUpdatedEvent.class).count());
    }

    @ParameterizedTest
    @ValueSource(ints = {1000, 7300})
    @DisplayName("Ордер в поиске реквизитов должен отменяться с событием смены статуса.")
    void cancelOrder_shouldCancelSearchingOrder(int amount) {
        Client client = getDummyClient();
        Order order = orderService.createOrder(client, UUID.randomUUID().toString(), amount, null);

        orderApiService.cancelOrder(client.getId(), order.getId());

        assertEquals(OrderStatus.CANCELED, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(List.of(new OrderStatusUpdatedEvent(order.getId(), OrderStatus.CANCELED,
                        OrderStatusChangeSource.CLIENT, true)),
                applicationEvents.stream(OrderStatusUpdatedEvent.class).toList());
    }

    @ParameterizedTest
    @ValueSource(ints = {1000, 7300})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Ордер в статусе SEARCHING должен быть закоммичен до запроса реквизитов.")
    void createOrder_shouldCommitSearchingOrderBeforeDetailsRequest(int amount) {
        Client client = getDummyClient();
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID().toString(), amount, Set.of(RequestMethod.CARD), null, "1235153208"
        );
        when(merchantDetailsReceiver.getDetails(any(), any(), any(), eq(30))).thenAnswer(invocation -> {
            Order committed = orderRepository.findById(invocation.getArgument(1, UUID.class)).orElseThrow();
            assertEquals(OrderStatus.SEARCHING, committed.getStatus());
            return Optional.empty();
        });
        try {
            ClientOrderSummary order = orderApiService.createOrder(command(client.getId(), request, 30, false));

            assertEquals(OrderStatus.DETAILS_NOT_FOUND, order.status());
            verify(merchantDetailsReceiver).getDetails(any(), any(), any(), eq(30));
        } finally {
            orderRepository.deleteAll();
            clientRepository.deleteAll();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1000, 7300})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Если клиент отменил ордер во время поиска и поиск прервался, ордер должен остаться отменённым.")
    void createOrder_shouldKeepOrderCanceledIfCanceledByClientDuringSearch(int amount) {
        Client client = getDummyClient();
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID().toString(), amount, Set.of(RequestMethod.CARD), null, "1235153208"
        );
        when(merchantDetailsReceiver.getDetails(any(), any(), any(), eq(30))).thenAnswer(invocation -> {
            orderApiService.cancelOrder(client.getId(), invocation.getArgument(1, UUID.class));
            return Optional.empty();
        });
        try {
            ClientOrderSummary order = orderApiService.createOrder(command(client.getId(), request, 30, false));

            assertEquals(OrderStatus.CANCELED, order.status());
            assertNull(order.details());
            assertEquals(OrderStatus.CANCELED, orderRepository.findById(order.id()).orElseThrow().getStatus());
        } finally {
            orderRepository.deleteAll();
            clientRepository.deleteAll();
        }
    }

    private Runnable captureSearchTask() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(virtualTaskExecutor).execute(captor.capture());
        return captor.getValue();
    }

    private CreateOrderRequest asyncRequest(String callbackUrl) {
        return new CreateOrderRequest(UUID.randomUUID().toString(), 5000, Set.of(RequestMethod.CARD), callbackUrl,
                "1235153208");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "https://example.com/callback,null,https://example.com/callback",
            "null,https://google.com/merchant-details/callback,https://google.com/merchant-details/callback",
            "https://example.com/callback,https://google.com/merchant-details/callback,https://example.com/callback",
            "null,null,null"
    }, nullValues = "null")
    @DisplayName("Асинхронное создание должно вернуть ордер в SEARCHING и запустить поиск в пуле, не дожидаясь его.")
    void createOrder_shouldReturnSearchingOrderAndSubmitSearchIfAsync(String requestCallbackUrl,
                                                                      String clientCallbackUrl,
                                                                      String expectedCallbackUrl) {
        Client client = getDummyClient();
        client.setCallbackUrl(clientCallbackUrl);

        ClientOrderSummary order = orderApiService.createOrderAsync(
                command(client.getId(), asyncRequest(requestCallbackUrl), 30, false), onSearchCompleted);

        assertEquals(OrderStatus.SEARCHING, order.status());
        assertEquals(expectedCallbackUrl, order.callbackUrl());
        assertEquals(OrderStatus.SEARCHING, orderRepository.findById(order.id()).orElseThrow().getStatus());
        verify(virtualTaskExecutor).execute(any());
        verifyNoInteractions(merchantDetailsReceiver, randomDetailsReceiver);
        assertEquals(0, applicationEvents.stream(OrderStatusUpdatedEvent.class).count());
    }

    @MethodSource("detailsResponseArguments")
    @ParameterizedTest
    @DisplayName("После асинхронного поиска с найденными реквизитами ордер должен перейти в NEW с КБ клиенту.")
    void createOrder_shouldMoveOrderToNewWithClientCallbackIfAsync(ApiDetailsResponse apiDetailsResponse) {
        Client client = getDummyClient();
        when(merchantDetailsReceiver.getDetails(eq(client.getId()), any(), any(), eq(45)))
                .thenReturn(Optional.of(apiDetailsResponse));
        ClientOrderSummary created = orderApiService.createOrderAsync(
                command(client.getId(), asyncRequest("https://example.com/callback"), 45, false),
                onSearchCompleted);

        captureSearchTask().run();

        Order order = orderRepository.findById(created.id()).orElseThrow();
        assertAll(
                () -> assertEquals(OrderStatus.NEW, order.getStatus()),
                () -> assertEquals(apiDetailsResponse.details().details(), order.getDetails()),
                () -> assertEquals(apiDetailsResponse.merchant(), order.getMerchant()),
                () -> assertNotNull(order.getExpiresAt())
        );
        assertEquals(List.of(new OrderStatusUpdatedEvent(order.getId(), OrderStatus.NEW,
                        OrderStatusChangeSource.SYSTEM, true)),
                applicationEvents.stream(OrderStatusUpdatedEvent.class).toList());
        verify(merchantCallbackProcessor).process(created.id());
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 60})
    @DisplayName("Если асинхронный поиск не нашёл реквизиты, ордер должен перейти в DETAILS_NOT_FOUND с КБ клиенту.")
    void createOrder_shouldMoveOrderToDetailsNotFoundWithClientCallbackIfAsync(int timeoutSeconds) {
        Client client = getDummyClient();
        when(merchantDetailsReceiver.getDetails(any(), any(), any(), eq(timeoutSeconds))).thenReturn(Optional.empty());
        ClientOrderSummary created = orderApiService.createOrderAsync(
                command(client.getId(), asyncRequest("https://example.com/callback"), timeoutSeconds, false),
                onSearchCompleted);

        captureSearchTask().run();

        Order order = orderRepository.findById(created.id()).orElseThrow();
        assertEquals(OrderStatus.DETAILS_NOT_FOUND, order.getStatus());
        assertNull(order.getDetails());
        assertEquals(List.of(new OrderStatusUpdatedEvent(order.getId(), OrderStatus.DETAILS_NOT_FOUND,
                        OrderStatusChangeSource.SYSTEM, true)),
                applicationEvents.stream(OrderStatusUpdatedEvent.class).toList());
        verify(merchantCallbackProcessor, never()).process(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Connection is not available", "Deadlock found"})
    @DisplayName("Непредвиденная ошибка асинхронного поиска не должна пробрасываться из задачи, ордер остаётся в SEARCHING, создаётся инцидент.")
    void createOrder_shouldNotThrowFromSearchTaskIfUnexpectedError(String message) {
        Client client = getDummyClient();
        when(merchantDetailsReceiver.getDetails(any(), any(), any(), eq(30)))
                .thenThrow(new IllegalStateException(message));
        ClientOrderSummary created = orderApiService.createOrderAsync(
                command(client.getId(), asyncRequest("https://example.com/callback"), 30, false), onSearchCompleted);

        long before = System.currentTimeMillis();
        assertDoesNotThrow(captureSearchTask()::run);

        assertEquals(OrderStatus.SEARCHING, orderRepository.findById(created.id()).orElseThrow().getStatus());
        assertEquals(0, applicationEvents.stream(OrderStatusUpdatedEvent.class).count());
        List<AsyncDetailsSearchErrorEvent> events = applicationEvents.stream(AsyncDetailsSearchErrorEvent.class)
                .toList();
        assertEquals(1, events.size());
        assertAll(
                () -> assertEquals(created.id(), events.getFirst().orderId()),
                () -> assertEquals(message, events.getFirst().error()),
                () -> assertTrue(events.getFirst().errorTimestamp() >= before)
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {1000, 7300})
    @DisplayName("Если клиент отменил ордер во время асинхронного поиска, ордер должен остаться отменённым.")
    void createOrder_shouldKeepOrderCanceledIfCanceledDuringAsyncSearch(int amount) {
        Client client = getDummyClient();
        CreateOrderRequest request = new CreateOrderRequest(UUID.randomUUID().toString(), amount,
                Set.of(RequestMethod.SBP), "https://example.com/callback", "1235153208");
        when(merchantDetailsReceiver.getDetails(any(), any(), any(), eq(30))).thenAnswer(invocation -> {
            orderApiService.cancelOrder(client.getId(), invocation.getArgument(1, UUID.class));
            return Optional.empty();
        });
        ClientOrderSummary created = orderApiService.createOrderAsync(command(client.getId(), request, 30, false),
                onSearchCompleted);

        captureSearchTask().run();

        assertEquals(OrderStatus.CANCELED, orderRepository.findById(created.id()).orElseThrow().getStatus());
        assertEquals(List.of(new OrderStatusUpdatedEvent(created.id(), OrderStatus.CANCELED,
                        OrderStatusChangeSource.CLIENT, true)),
                applicationEvents.stream(OrderStatusUpdatedEvent.class).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a27fc526-017c-440a-b680-0f672983aeaf", "internal-7731"})
    @DisplayName("Синхронное создание без адреса КБ в запросе и у клиента должно создавать ордер.")
    void createOrder_shouldCreateOrderIfSyncWithoutCallbackUrl(String internalId) {
        Client client = getDummyClient();
        CreateOrderRequest request = new CreateOrderRequest(internalId, 5000, Set.of(RequestMethod.CARD), null,
                "1235153208");
        when(merchantDetailsReceiver.getDetails(any(), any(), any(), eq(30))).thenReturn(Optional.empty());

        ClientOrderSummary order = orderApiService.createOrder(command(client.getId(), request, 30, false));

        assertEquals(OrderStatus.DETAILS_NOT_FOUND, order.status());
        verifyNoInteractions(virtualTaskExecutor);
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 60})
    @DisplayName("Колбэк завершения должен вызываться только после окончания асинхронного поиска.")
    void createOrderAsync_shouldRunCallbackAfterSearch(int timeoutSeconds) {
        Client client = getDummyClient();
        when(merchantDetailsReceiver.getDetails(any(), any(), any(), eq(timeoutSeconds))).thenReturn(Optional.empty());
        orderApiService.createOrderAsync(
                command(client.getId(), asyncRequest("https://example.com/callback"), timeoutSeconds, false),
                onSearchCompleted);
        verifyNoInteractions(onSearchCompleted);

        captureSearchTask().run();

        verify(onSearchCompleted).accept(null, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Connection is not available", "Deadlock found"})
    @DisplayName("Колбэк завершения должен вызываться, если асинхронный поиск упал с ошибкой.")
    void createOrderAsync_shouldRunCallbackIfSearchFailed(String message) {
        Client client = getDummyClient();
        when(merchantDetailsReceiver.getDetails(any(), any(), any(), eq(30)))
                .thenThrow(new IllegalStateException(message));
        orderApiService.createOrderAsync(
                command(client.getId(), asyncRequest("https://example.com/callback"), 30, false), onSearchCompleted);

        captureSearchTask().run();

        verify(onSearchCompleted).accept(null, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a27fc526-017c-440a-b680-0f672983aeaf", "internal-7731"})
    @DisplayName("Если ордер не создан, поиск не должен запускаться, а колбэк завершения вызываться.")
    void createOrderAsync_shouldNotRunCallbackIfCreationFailed(String internalId) {
        Client client = getDummyClient();
        CreateOrderRequest request = new CreateOrderRequest(internalId, 5000, Set.of(RequestMethod.CARD), null,
                "1235153208");
        orderService.createOrder(client, internalId, 5000, null);

        assertThrows(BadRequestException.class,
                () -> orderApiService.createOrderAsync(command(client.getId(), request, 30, false),
                        onSearchCompleted));

        verifyNoInteractions(virtualTaskExecutor, onSearchCompleted);
    }
}
