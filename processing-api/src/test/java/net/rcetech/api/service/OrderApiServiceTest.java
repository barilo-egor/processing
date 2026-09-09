package net.rcetech.api.service;

import net.rcetech.api.dto.ApiDetailsResponse;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.meta.orders.dto.ClientOrderFilter;
import net.rcetech.meta.orders.dto.OrderSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderApiServiceTest {

    @TestConfiguration
    static class OrderApiServiceTestContextConfiguration {

        @Bean
        public OrderService orderService(OrderRepository orderRepository) {
            return new OrderService(orderRepository);
        }

        @Bean
        public ClientMapper clientMapper() {
            return Mappers.getMapper(ClientMapper.class);
        }

        @Bean
        public ClientService clientService(ClientRepository clientRepository, ClientMapper clientMapper) {
            return new ClientService(clientRepository, clientMapper);
        }
    }

    @Container
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.0.46");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:database/migration");
    }

    @MockitoBean
    private ApiMerchantDetailsGrpcService apiMerchantDetailsGrpcService;

    @Autowired
    private ClientService clientService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    private OrderApiService orderApiService;

    @BeforeEach
    void setUp() {
        orderApiService = new OrderApiService(apiMerchantDetailsGrpcService, clientService, orderService);
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
        assertThrows(BaseException.class, () -> orderApiService.createOrder(clientId, null));
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
                UUID.randomUUID().toString(), 1000, Set.of(RequestMethod.CARD), true,
                null, "1235153208"
        );
    }

    Client getDummyClient() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test" + client.getId());
        client.setRegisteredAt(Instant.now());
        client.setStatus(ClientStatus.ACTIVE);
        return clientService.save(client);
    }

    @ParameterizedTest
    @ValueSource(ints = {
            800, 1500
    })
    @DisplayName("Метод должен создать ордер, срок которого истекает через время, установленное клиенту.")
    void createOrder_shouldCreateOrderWithExpiresAtWithClientOrderTimeout(int orderTimeout) {
        ApiDetailsResponse detailsResponse = getDummyApiDetailsResponse();
        when(apiMerchantDetailsGrpcService.getDetails(any(), any())).thenReturn(detailsResponse);
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test");
        client.setRegisteredAt(Instant.now());
        client.setStatus(ClientStatus.ACTIVE);
        client.setOrderTimeoutSeconds(orderTimeout);
        clientService.save(client);
        Instant time = Instant.now();
        try (MockedStatic<Instant> instantMock = Mockito.mockStatic(Instant.class)) {
            instantMock.when(Instant::now).thenReturn(time);
            orderApiService.createOrder(client.getId(), getDummyCreateOrderRequest());
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
        when(apiMerchantDetailsGrpcService.getDetails(any(), any())).thenReturn(detailsResponse);
        Client client = getDummyClient();
        clientService.save(client);
        orderApiService.createOrder(client.getId(), createOrderRequest);
        List<Order> orders = orderRepository.findAll();
        assertEquals(1, orders.size());
        Order order = orders.getFirst();
        assertAll(
                () -> assertEquals(createOrderRequest.internalId(), order.getInternalId()),
                () -> assertEquals(createOrderRequest.amount(), order.getAmount()),
                () -> assertEquals(createOrderRequest.enableUniqueAmount(), order.getEnableUniqueAmount()),
                () -> assertEquals(createOrderRequest.callbackUrl(), order.getCallbackUrl())
        );
    }

    static Stream<Arguments> createOrderRequestArguments() {
        return Stream.of(
                Arguments.of(
                        new CreateOrderRequest(
                                UUID.randomUUID().toString(), 1000, Set.of(RequestMethod.CARD), true,
                                "https://example.com/callback", "1235153208"
                        )
                ),
                Arguments.of(
                        new CreateOrderRequest(
                                UUID.randomUUID().toString(), 5452, Set.of(RequestMethod.CARD, RequestMethod.SBP), false,
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
        when(apiMerchantDetailsGrpcService.getDetails(any(), any())).thenReturn(apiDetailsResponse);
        orderApiService.createOrder(client.getId(), getDummyCreateOrderRequest());
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

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/callback",
            "https://google.com/merchant-details/callback"
    })
    @DisplayName("Метод должен создать ордер с callback url сохраненным за клиентом.")
    void createOrder_shouldCreateOrderWithClientCallbackUrl(String url) {
        ApiDetailsResponse apiDetailsResponse = getDummyApiDetailsResponse();
        when(apiMerchantDetailsGrpcService.getDetails(any(), any())).thenReturn(apiDetailsResponse);
        Client client = getDummyClient();
        client.setCallbackUrl(url);
        orderApiService.createOrder(client.getId(), getDummyCreateOrderRequest());
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
        assertThrows(BadRequestException.class, () -> orderApiService.cancelOrder(orderId, id), "Order not found");
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
                "Order not found");
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
            if (order.getId().equals(targetOrder.getId())) {
                assertEquals(OrderStatus.CANCELED, order.getStatus());
            } else {
                assertEquals(OrderStatus.NEW, order.getStatus());
            }
        }
    }

    @Test
    void findAll_shouldReturnOnlyTargetClientOrders() {
        Client targetClient = getDummyClient();
        List<Order> expectedOrders = List.of(getDummyOrder(targetClient), getDummyOrder(targetClient));
        Client anotherClient = getDummyClient();
        getDummyOrder(anotherClient);

        Page<OrderSummary> actual = orderApiService.findAll(
                targetClient.getId(),
                new ClientOrderFilter(null, null), Pageable.ofSize(10)
        );

        assertEquals(
                expectedOrders.stream().map(Order::getId).collect(Collectors.toSet()),
                actual.getContent().stream().map(OrderSummary::id).collect(Collectors.toSet())
        );
    }

    @ValueSource(strings = { "TIMEOUT", "SUCCESS" })
    @ParameterizedTest
    void findAll_shouldFilterOrdersByStatus(OrderStatus status) {
        Client client = getDummyClient();
        Order nonTargetOrder = getDummyOrder(client);
        assertNotEquals(nonTargetOrder.getStatus(), status);
        Order targerOrder = getDummyOrder(client);
        targerOrder.setStatus(status);

        Page<OrderSummary> actual = orderApiService.findAll(
                client.getId(),
                new ClientOrderFilter(status, null), Pageable.ofSize(10)
        );

        assertEquals(1, actual.getContent().size());
        assertEquals(targerOrder.getId(), actual.getContent().getFirst().id());
    }

    @ValueSource(strings = { "QR", "SBP" })
    @ParameterizedTest
    void findAll_shouldFilterOrdersByStatus(RequestMethod method) {
        Client client = getDummyClient();
        Order nonTargetOrder = getDummyOrder(client);
        assertNotEquals(nonTargetOrder.getMethod(), method);
        Order targerOrder = getDummyOrder(client);
        targerOrder.setMethod(method);

        Page<OrderSummary> actual = orderApiService.findAll(
                client.getId(),
                new ClientOrderFilter(null, method), Pageable.ofSize(10)
        );

        assertEquals(1, actual.getContent().size());
        assertEquals(targerOrder.getId(), actual.getContent().getFirst().id());
    }
}