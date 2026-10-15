package net.rcetech.domain.service.orders;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.domain.repository.orders.OrderSpecifications;
import net.rcetech.meta.exception.OrderStatusUpdateException;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.meta.orders.dto.OrderFilter;
import net.rcetech.meta.orders.dto.OrderSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrderService.class)
@Testcontainers
@RecordApplicationEvents
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Container
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.0.46");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
    }

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    Client getDummyClient() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test" + client.getId());
        client.setRegisteredAt(Instant.now());
        return clientRepository.save(client);
    }

    Order getDummyOrder(Client client) {
        Order order = new Order();
        fillFields(order, client);
        return orderRepository.save(order);
    }

    void fillFields(Order order, Client client) {
        if (order.getId() == null) {
            order.setId(UUID.randomUUID());
        }
        if (order.getCreatedAt() == null) {
            order.setCreatedAt(Instant.now());
        }
        if (order.getExpiresAt() == null) {
            order.setExpiresAt(Instant.now().plusSeconds(900));
        }
        if (order.getClient() == null && client != null) {
            order.setClient(client);
        }
        if (order.getInternalId() == null) {
            order.setInternalId(UUID.randomUUID().toString());
        }
        if (order.getStatus() == null) {
            order.setStatus(OrderStatus.NEW);
        }
        if (order.getAmount() == null) {
            order.setAmount(5000);
        }
        if (order.getMerchant() == null) {
            order.setMerchant(Merchant.ALFA_TEAM);
        }
        if (order.getMerchantOrderId() == null) {
            order.setMerchantOrderId(UUID.randomUUID().toString());
        }
        if (order.getMerchantOrderStatus() == null) {
            order.setMerchantOrderStatus("NEW");
        }
        if (order.getMethod() == null) {
            order.setMethod(RequestMethod.CARD);
        }
        if (order.getDetails() == null) {
            order.setDetails("1234 1234 1234 1234");
        }
        if (order.getBank() == null) {
            order.setBank("ALFA");
        }
        if (order.getCallbackUrl() == null) {
            order.setCallbackUrl("https://google.com/callback");
        }
    }

    @Test
    @DisplayName("Метод должен вернуть все ордера, если не задан фильтр.")
    void findAll_shouldReturnAllOrdersWithNullFilter() {
        Client client = getDummyClient();
        getDummyOrder(client);
        getDummyOrder(client);

        Page<OrderSummary> actual = orderService.findAll(
                OrderSpecifications.matches(null),
                PageRequest.of(0, 10),
                OrderSummary.class
        );

        assertEquals(2, actual.getTotalElements());
    }

    @Test
    @DisplayName("Метод должен вернуть все ордера, если объект фильтров равен null.")
    void findAll_shouldReturnAllOrdersWithEmptyFilter() {
        Client client = getDummyClient();
        getDummyOrder(client);
        getDummyOrder(client);

        OrderFilter orderFilter = new OrderFilter(
                null, null, null, null, null,
                null, null, null
        );
        Page<OrderSummary> actual = orderService.findAll(
                OrderSpecifications.matches(orderFilter),
                PageRequest.of(0, 10),
                OrderSummary.class
        );

        assertEquals(2, actual.getTotalElements());
    }

    @RepeatedTest(value = 2)
    @DisplayName("Метод должен вернуть ордер, если передан фильтр по id.")
    void findAll_shouldReturnOrderById() {
        Client client = getDummyClient();
        Order order = getDummyOrder(client);
        getDummyOrder(client);

        OrderFilter orderFilter = new OrderFilter(
                order.getId(), null, null, null, null,
                null, null, null
        );
        Page<OrderSummary> actual = orderService.findAll(
                OrderSpecifications.matches(orderFilter),
                PageRequest.of(0, 10),
                OrderSummary.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(order.getId(), actual.getContent().getFirst().getId());
    }

    @ValueSource(longs = {
            1789401782887L, 1789401732887L
    })
    @ParameterizedTest
    @DisplayName("Метод должен вернуть ордера, отфильтрованные по дате регистрации ОТ.")
    void findAll_shouldReturnOrderByCreatedFrom(long millis) {
        Client client = getDummyClient();
        Order order = new Order();
        order.setCreatedAt(Instant.ofEpochMilli(millis + 5000));
        fillFields(order, client);
        Order nonTargetOrder = new Order();
        nonTargetOrder.setCreatedAt(Instant.ofEpochMilli(millis - 1000));
        fillFields(nonTargetOrder, client);
        orderRepository.save(order);
        orderRepository.save(nonTargetOrder);

        OrderFilter orderFilter = new OrderFilter(
                null, Instant.ofEpochMilli(millis), null, null, null,
                null, null, null
        );
        Page<OrderSummary> actual = orderService.findAll(
                OrderSpecifications.matches(orderFilter),
                PageRequest.of(0, 10),
                OrderSummary.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(order.getId(), actual.getContent().getFirst().getId());
    }

    @ValueSource(longs = {
            1789401782887L, 1789401732887L
    })
    @ParameterizedTest
    @DisplayName("Метод должен вернуть ордера, отфильтрованные по дате регистрации ДО.")
    void findAll_shouldReturnOrderByCreatedTo(long millis) {
        Client client = getDummyClient();
        Order order = new Order();
        order.setCreatedAt(Instant.ofEpochMilli(millis - 1000));
        fillFields(order, client);
        Order nonTargetOrder = new Order();
        nonTargetOrder.setCreatedAt(Instant.ofEpochMilli(millis + 2000));
        fillFields(nonTargetOrder, client);
        orderRepository.save(order);
        orderRepository.save(nonTargetOrder);

        OrderFilter orderFilter = new OrderFilter(
                null, null, Instant.ofEpochMilli(millis), null, null,
                null, null, null
        );
        Page<OrderSummary> actual = orderService.findAll(
                OrderSpecifications.matches(orderFilter),
                PageRequest.of(0, 10),
                OrderSummary.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(order.getId(), actual.getContent().getFirst().getId());
    }

    @RepeatedTest(value = 2)
    @DisplayName("Метод должен вернуть ордера, отфильтрованные по идентификатору клиента.")
    void findAll_shouldReturnOrderByClientId() {
        Client client = getDummyClient();
        assertNotNull(client.getId());
        Order order = new Order();
        fillFields(order, client);
        Client nonTargetClient = getDummyClient();
        Order nonTargetOrder = new Order();
        fillFields(nonTargetOrder, nonTargetClient);
        orderRepository.save(order);
        orderRepository.save(nonTargetOrder);

        OrderFilter orderFilter = new OrderFilter(
                null, null, null, client.getId().toString(), null,
                null, null, null
        );
        Page<OrderSummary> actual = orderService.findAll(
                OrderSpecifications.matches(orderFilter),
                PageRequest.of(0, 10),
                OrderSummary.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(order.getId(), actual.getContent().getFirst().getId());
    }

    @RepeatedTest(value = 2)
    @DisplayName("Метод должен вернуть ордера, отфильтрованные по логину клиента.")
    void findAll_shouldReturnOrderByClientUsername() {
        Client client = getDummyClient();
        assertNotNull(client.getId());
        Order order = new Order();
        fillFields(order, client);
        Client nonTargetClient = getDummyClient();
        Order nonTargetOrder = new Order();
        fillFields(nonTargetOrder, nonTargetClient);
        orderRepository.save(order);
        orderRepository.save(nonTargetOrder);

        OrderFilter orderFilter = new OrderFilter(
                null, null, null, "test" + client.getId(), null,
                null, null, null
        );
        Page<OrderSummary> actual = orderService.findAll(
                OrderSpecifications.matches(orderFilter),
                PageRequest.of(0, 10),
                OrderSummary.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(order.getId(), actual.getContent().getFirst().getId());
    }

    @ValueSource(strings = {
            "1789401782887", "a4b4746f-46e4-46e0-a077-348b0f914a36"
    })
    @ParameterizedTest
    @DisplayName("Метод должен вернуть ордера, отфильтрованные по идентификатору в системе клиента.")
    void findAll_shouldReturnOrderByInternalId(String internalId) {
        Client client = getDummyClient();
        Order order = new Order();
        order.setInternalId(internalId);
        fillFields(order, client);
        Order nonTargetOrder = new Order();
        fillFields(nonTargetOrder, client);
        orderRepository.save(order);
        orderRepository.save(nonTargetOrder);

        OrderFilter orderFilter = new OrderFilter(
                null, null, null, null, internalId,
                null, null, null
        );
        Page<OrderSummary> actual = orderService.findAll(
                OrderSpecifications.matches(orderFilter),
                PageRequest.of(0, 10),
                OrderSummary.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(order.getId(), actual.getContent().getFirst().getId());
    }

    @ValueSource(strings = {
            "NEW", "SUCCESS"
    })
    @ParameterizedTest
    @DisplayName("Метод должен вернуть ордера, отфильтрованные по статусу.")
    void findAll_shouldReturnOrderByStatus(OrderStatus orderStatus) {
        Client client = getDummyClient();
        Order order = new Order();
        order.setStatus(orderStatus);
        fillFields(order, client);
        Order nonTargetOrder = new Order();
        nonTargetOrder.setStatus(OrderStatus.CANCELED);
        fillFields(nonTargetOrder, client);
        orderRepository.save(order);
        orderRepository.save(nonTargetOrder);

        OrderFilter orderFilter = new OrderFilter(
                null, null, null, null, null,
                orderStatus, null, null
        );
        Page<OrderSummary> actual = orderService.findAll(
                OrderSpecifications.matches(orderFilter),
                PageRequest.of(0, 10),
                OrderSummary.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(order.getId(), actual.getContent().getFirst().getId());
    }

    @ValueSource(strings = {
            "ALFA_TEAM", "EVO_PAY"
    })
    @ParameterizedTest
    @DisplayName("Метод должен вернуть ордера, отфильтрованные по мерчанту.")
    void findAll_shouldReturnOrderByMerchant(Merchant merchant) {
        Client client = getDummyClient();
        Order order = new Order();
        order.setMerchant(merchant);
        fillFields(order, client);
        Order nonTargetOrder = new Order();
        nonTargetOrder.setMerchant(Merchant.ONLY_PAYS);
        fillFields(nonTargetOrder, client);
        orderRepository.save(order);
        orderRepository.save(nonTargetOrder);

        OrderFilter orderFilter = new OrderFilter(
                null, null, null, null, null,
                null, merchant, null
        );
        Page<OrderSummary> actual = orderService.findAll(
                OrderSpecifications.matches(orderFilter),
                PageRequest.of(0, 10),
                OrderSummary.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(order.getId(), actual.getContent().getFirst().getId());
    }

    @ValueSource(strings = {
            "1789401782887", "a4b4746f-46e4-46e0-a077-348b0f914a36"
    })
    @ParameterizedTest
    @DisplayName("Метод должен вернуть ордера, отфильтрованные по идентификатору в системе мерчанта.")
    void findAll_shouldReturnOrderByMerchantOrderId(String merchantOrderId) {
        Client client = getDummyClient();
        Order order = new Order();
        order.setMerchantOrderId(merchantOrderId);
        fillFields(order, client);
        Order nonTargetOrder = new Order();
        fillFields(nonTargetOrder, client);
        orderRepository.save(order);
        orderRepository.save(nonTargetOrder);

        OrderFilter orderFilter = new OrderFilter(
                null, null, null, null, null,
                null, null, merchantOrderId
        );
        Page<OrderSummary> actual = orderService.findAll(
                OrderSpecifications.matches(orderFilter),
                PageRequest.of(0, 10),
                OrderSummary.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(order.getId(), actual.getContent().getFirst().getId());
    }

    @Test
    @DisplayName("Метод должен вернуть ордера, если в String полях переданы пустые строки.")
    void findAll_shouldReturnAllOrdersIfStringFieldsIsBlank() {
        Client client = getDummyClient();
        getDummyOrder(client);
        getDummyOrder(client);

        Page<OrderSummary> actual = orderService.findAll(
                OrderSpecifications.matches(new OrderFilter(
                        null, null, null, "", "   ",
                        null, null, ""
                )),
                PageRequest.of(0, 10),
                OrderSummary.class
        );

        assertEquals(2, actual.getTotalElements());
    }

    void changeStatus(OrderStatus statusTo, UUID id, String merchantOrderStatus) {
        switch (statusTo) {
            case SUCCESS -> orderService.confirm(id, merchantOrderStatus);
            case CANCELED -> orderService.cancel(id, merchantOrderStatus);
            case TIMEOUT -> orderService.timeout(id, merchantOrderStatus);
            case DISPUTE -> orderService.dispute(id, merchantOrderStatus);
            case NEW -> throw new IllegalArgumentException("Нет метода перехода в статус NEW.");
        }
    }

    Order saveOrder(OrderStatus status, String merchantOrderStatus) {
        Order order = new Order();
        order.setStatus(status);
        order.setMerchantOrderStatus(merchantOrderStatus);
        fillFields(order, getDummyClient());
        return orderRepository.save(order);
    }

    @CsvSource({
            "NEW,SUCCESS,PAID",
            "NEW,CANCELED,DECLINED",
            "NEW,TIMEOUT,EXPIRED",
            "NEW,DISPUTE,ARBITRATION",
            "CANCELED,SUCCESS,PAID",
            "CANCELED,DISPUTE,DISPUTE",
            "TIMEOUT,SUCCESS,SUCCESS",
            "TIMEOUT,DISPUTE,ARBITRATION",
            "DISPUTE,SUCCESS,PAID",
            "DISPUTE,CANCELED,REJECTED"
    })
    @ParameterizedTest
    @DisplayName("Метод должен перевести ордер в новый статус, сохранить статус мерчанта и опубликовать событие, если переход разрешён.")
    void changeStatus_shouldApplyAllowedTransition(OrderStatus statusFrom, OrderStatus statusTo,
                                                   String merchantOrderStatus) {
        Order order = saveOrder(statusFrom, "OLD");

        changeStatus(statusTo, order.getId(), merchantOrderStatus);

        Order actual = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(statusTo, actual.getStatus());
        assertEquals(merchantOrderStatus, actual.getMerchantOrderStatus());
        List<OrderStatusUpdatedEvent> events = applicationEvents.stream(OrderStatusUpdatedEvent.class).toList();
        assertEquals(1, events.size());
        assertEquals(order.getId(), events.getFirst().orderId());
        assertEquals(statusTo, events.getFirst().orderStatus());
    }

    @CsvSource({
            "SUCCESS,SUCCESS,PAID",
            "SUCCESS,TIMEOUT,EXPIRED",
            "CANCELED,CANCELED,DECLINED",
            "CANCELED,TIMEOUT,EXPIRED",
            "TIMEOUT,CANCELED,DECLINED",
            "TIMEOUT,TIMEOUT,EXPIRED",
            "DISPUTE,TIMEOUT,EXPIRED",
            "DISPUTE,DISPUTE,ARBITRATION"
    })
    @ParameterizedTest
    @DisplayName("Метод должен пропустить переход без исключения, изменения ордера и события, если переход пропускается.")
    void changeStatus_shouldSkipTransition(OrderStatus statusFrom, OrderStatus statusTo, String merchantOrderStatus) {
        Order order = saveOrder(statusFrom, "OLD");

        assertDoesNotThrow(() -> changeStatus(statusTo, order.getId(), merchantOrderStatus));

        Order actual = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(statusFrom, actual.getStatus());
        assertEquals("OLD", actual.getMerchantOrderStatus());
        assertEquals(0, applicationEvents.stream(OrderStatusUpdatedEvent.class).count());
    }

    @CsvSource({
            "CANCELED,DECLINED,Переход ордера из статуса «Успешный» в статус «Отменен» запрещён.",
            "DISPUTE,ARBITRATION,Переход ордера из статуса «Успешный» в статус «В споре» запрещён."
    })
    @ParameterizedTest
    @DisplayName("Метод должен выбросить OrderStatusUpdateException и не менять ордер, если переход запрещён.")
    void changeStatus_shouldThrowIfTransitionRejected(OrderStatus statusTo, String merchantOrderStatus,
                                                      String expectedMessage) {
        Order order = saveOrder(OrderStatus.SUCCESS, "PAID");
        UUID orderId = order.getId();

        OrderStatusUpdateException exception = assertThrows(OrderStatusUpdateException.class,
                () -> changeStatus(statusTo, orderId, merchantOrderStatus));

        assertEquals(OrderStatus.SUCCESS, exception.getStatusFrom());
        assertEquals(statusTo, exception.getStatusTo());
        assertEquals(expectedMessage, exception.getMessage());
        Order actual = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.SUCCESS, actual.getStatus());
        assertEquals("PAID", actual.getMerchantOrderStatus());
        assertEquals(0, applicationEvents.stream(OrderStatusUpdatedEvent.class).count());
    }

    @ValueSource(strings = {
            "NEW",
            "WAITING"
    })
    @ParameterizedTest
    @DisplayName("Метод должен перевести ордер в TIMEOUT без изменения статуса мерчанта, если статус мерчанта не передан.")
    void timeout_shouldKeepMerchantOrderStatusIfNull(String merchantOrderStatus) {
        Order order = saveOrder(OrderStatus.NEW, merchantOrderStatus);

        orderService.timeout(order.getId(), null);

        Order actual = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.TIMEOUT, actual.getStatus());
        assertEquals(merchantOrderStatus, actual.getMerchantOrderStatus());
    }
}
