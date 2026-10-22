package net.rcetech.domain.service.orders;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.domain.repository.orders.OrderSpecifications;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.exception.ExceptionMessage;
import net.rcetech.meta.exception.OrderStatusUpdateException;
import net.rcetech.meta.orders.*;
import net.rcetech.meta.orders.dto.ClientOrderSummary;
import net.rcetech.meta.orders.dto.OrderFilter;
import net.rcetech.meta.orders.dto.OrderSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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

    @ValueSource(longs = {
            1789401782000L, 1789401733000L
    })
    @ParameterizedTest
    @DisplayName("Метод должен включать границу даты создания ОТ.")
    void findAll_shouldIncludeCreatedFromBoundary(long millis) {
        Instant from = Instant.ofEpochMilli(millis);
        Client client = getDummyClient();
        Order order = saveOrder(client, from);
        saveOrder(client, from.minusSeconds(1));

        OrderFilter orderFilter = new OrderFilter(
                null, from, null, null, null,
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
            1789401782000L, 1789401733000L
    })
    @ParameterizedTest
    @DisplayName("Метод должен исключать границу даты создания ДО.")
    void findAll_shouldExcludeCreatedToBoundary(long millis) {
        Instant to = Instant.ofEpochMilli(millis);
        Client client = getDummyClient();
        saveOrder(client, to);
        Order order = saveOrder(client, to.minusSeconds(1));

        OrderFilter orderFilter = new OrderFilter(
                null, null, to, null, null,
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

    Order saveOrder(Client client, Instant createdAt) {
        Order order = new Order();
        order.setCreatedAt(createdAt);
        fillFields(order, client);
        return orderRepository.save(order);
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
    void updateStatus_shouldApplyAllowedTransition(OrderStatus statusFrom, OrderStatus statusTo,
                                                   String merchantOrderStatus) {
        Order order = saveOrder(statusFrom, "OLD");

        OrderStatusChange change = orderService.updateStatus(order.getId(), statusTo, OrderStatusChangeSource.MERCHANT_CALLBACK,
                merchantOrderStatus);

        assertEquals(new OrderStatusChange(statusFrom, statusTo, OrderStatusTransition.ALLOWED), change);
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
    void updateStatus_shouldSkipTransition(OrderStatus statusFrom, OrderStatus statusTo, String merchantOrderStatus) {
        Order order = saveOrder(statusFrom, "OLD");

        OrderStatusChange change = assertDoesNotThrow(
                () -> orderService.updateStatus(order.getId(), statusTo, OrderStatusChangeSource.MERCHANT_CALLBACK,
                merchantOrderStatus));

        assertEquals(new OrderStatusChange(statusFrom, statusTo, OrderStatusTransition.SKIPPED), change);
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
    void updateStatus_shouldThrowIfTransitionRejected(OrderStatus statusTo, String merchantOrderStatus,
                                                      String expectedMessage) {
        Order order = saveOrder(OrderStatus.SUCCESS, "PAID");
        UUID orderId = order.getId();

        OrderStatusUpdateException exception = assertThrows(OrderStatusUpdateException.class,
                () -> orderService.updateStatus(orderId, statusTo, OrderStatusChangeSource.MERCHANT_CALLBACK,
                        merchantOrderStatus));

        assertEquals(OrderStatus.SUCCESS, exception.getStatusFrom());
        assertEquals(statusTo, exception.getStatusTo());
        assertEquals(expectedMessage, exception.getMessage());
        Order actual = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.SUCCESS, actual.getStatus());
        assertEquals("PAID", actual.getMerchantOrderStatus());
        assertEquals(0, applicationEvents.stream(OrderStatusUpdatedEvent.class).count());
    }

    @CsvSource({
            "CANCELED,MERCHANT_CALLBACK,PAID",
            "TIMEOUT,MERCHANT_CALLBACK,SUCCESS",
            "CANCELED,SYSTEM,PAID",
            "DISPUTE,CLIENT,SUCCESS"
    })
    @ParameterizedTest
    @DisplayName("Метод должен выбросить OrderStatusUpdateException и не менять ордер, если ордер без реквизитов переводится в SUCCESS не поддержкой.")
    void updateStatus_shouldThrowIfSuccessWithoutDetailsNotBySupport(OrderStatus statusFrom,
                                                                     OrderStatusChangeSource source,
                                                                     String merchantOrderStatus) {
        Order order = saveOrderWithoutDetails(statusFrom);
        UUID orderId = order.getId();

        OrderStatusUpdateException exception = assertThrows(OrderStatusUpdateException.class,
                () -> orderService.updateStatus(orderId, OrderStatus.SUCCESS, source, merchantOrderStatus));

        assertEquals(statusFrom, exception.getStatusFrom());
        assertEquals(OrderStatus.SUCCESS, exception.getStatusTo());
        assertEquals("Переход ордера из статуса «" + statusFrom.getDescription()
                + "» в статус «Успешный» запрещён.", exception.getMessage());
        Order actual = orderRepository.findById(orderId).orElseThrow();
        assertEquals(statusFrom, actual.getStatus());
        assertEquals("OLD", actual.getMerchantOrderStatus());
        assertEquals(0, applicationEvents.stream(OrderStatusUpdatedEvent.class).count());
    }

    @CsvSource({
            "CANCELED,PAID",
            "DISPUTE,SUCCESS"
    })
    @ParameterizedTest
    @DisplayName("Метод должен перевести ордер без реквизитов в SUCCESS, если переводит поддержка.")
    void updateStatus_shouldAllowSuccessWithoutDetailsBySupport(OrderStatus statusFrom, String merchantOrderStatus) {
        Order order = saveOrderWithoutDetails(statusFrom);

        OrderStatusChange change = orderService.updateStatus(order.getId(), OrderStatus.SUCCESS,
                OrderStatusChangeSource.SUPPORT, merchantOrderStatus);

        assertEquals(new OrderStatusChange(statusFrom, OrderStatus.SUCCESS, OrderStatusTransition.ALLOWED), change);
        assertEquals(OrderStatus.SUCCESS, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(1, applicationEvents.stream(OrderStatusUpdatedEvent.class).count());
    }

    Order saveOrderWithoutDetails(OrderStatus status) {
        Order order = saveOrder(status, "OLD");
        order.setDetails(null);
        return orderRepository.save(order);
    }

    @ValueSource(strings = {
            "NEW",
            "WAITING"
    })
    @ParameterizedTest
    @DisplayName("Метод должен перевести ордер в TIMEOUT без изменения статуса мерчанта, если статус мерчанта не передан.")
    void updateStatus_shouldKeepMerchantOrderStatusIfNull(String merchantOrderStatus) {
        Order order = saveOrder(OrderStatus.NEW, merchantOrderStatus);

        orderService.updateStatus(order.getId(), OrderStatus.TIMEOUT, OrderStatusChangeSource.SYSTEM, null);

        Order actual = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.TIMEOUT, actual.getStatus());
        assertEquals(merchantOrderStatus, actual.getMerchantOrderStatus());
    }

    @CsvSource({
            "SEARCHING,NEW,SYSTEM,true,Т-Банк",
            "SEARCHING,DETAILS_NOT_FOUND,SYSTEM,false,Сбербанк",
            "NEW,SUCCESS,SUPPORT,false,ВТБ",
            "NEW,CANCELED,CLIENT,true,Альфа-Банк"
    })
    @ParameterizedTest
    @DisplayName("Метод должен применить изменения ордера и опубликовать событие с источником и признаком уведомления клиента, если переход разрешён.")
    void updateStatus_shouldApplyChangesAndPublishNotifyClientIfAllowed(OrderStatus statusFrom, OrderStatus statusTo,
                                                                         OrderStatusChangeSource source,
                                                                         boolean notifyClient, String bank) {
        Order order = saveOrder(statusFrom, "OLD");

        orderService.updateStatus(order.getId(), statusTo, source, notifyClient,
                orderToUpdate -> orderToUpdate.setBank(bank));

        Order actual = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(statusTo, actual.getStatus());
        assertEquals(bank, actual.getBank());
        assertEquals(List.of(new OrderStatusUpdatedEvent(order.getId(), statusTo, source, notifyClient)),
                applicationEvents.stream(OrderStatusUpdatedEvent.class).toList());
    }

    @CsvSource({
            "CANCELED,NEW,Т-Банк",
            "DETAILS_NOT_FOUND,NEW,Сбербанк"
    })
    @ParameterizedTest
    @DisplayName("Метод не должен применять изменения ордера, если переход пропускается.")
    void updateStatus_shouldNotApplyChangesIfSkipped(OrderStatus statusFrom, OrderStatus statusTo, String bank) {
        Order order = saveOrder(statusFrom, "OLD");

        OrderStatusChange change = orderService.updateStatus(order.getId(), statusTo, OrderStatusChangeSource.SYSTEM, false,
                orderToUpdate -> orderToUpdate.setBank(bank));

        assertEquals(OrderStatusTransition.SKIPPED, change.transition());
        Order actual = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(statusFrom, actual.getStatus());
        assertEquals("ALFA", actual.getBank());
        assertEquals(0, applicationEvents.stream(OrderStatusUpdatedEvent.class).count());
    }

    @ValueSource(strings = {
            "5000",
            "12000"
    })
    @ParameterizedTest
    @DisplayName("Ордер в статусе SEARCHING должен сохраняться без реквизитов, данных мерчанта и времени истечения.")
    void save_shouldSaveSearchingOrderWithoutDetails(int amount) {
        Order order = searchingOrder(getDummyClient(), amount);

        orderRepository.saveAndFlush(order);

        Order actual = orderRepository.findById(order.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(OrderStatus.SEARCHING, actual.getStatus()),
                () -> assertEquals(amount, actual.getAmount()),
                () -> assertNull(actual.getExpiresAt()),
                () -> assertNull(actual.getMerchant()),
                () -> assertNull(actual.getMerchantOrderId()),
                () -> assertNull(actual.getMerchantOrderStatus()),
                () -> assertNull(actual.getMethod()),
                () -> assertNull(actual.getDetails()),
                () -> assertNull(actual.getBank())
        );
    }

    @ValueSource(ints = {
            2,
            5
    })
    @ParameterizedTest
    @DisplayName("Несколько ордеров без мерчанта и идентификатора ордера мерчанта должны сохраняться без нарушения уникальности.")
    void save_shouldSaveSeveralOrdersWithoutMerchantOrderId(int count) {
        Client client = getDummyClient();

        for (int i = 0; i < count; i++) {
            orderRepository.saveAndFlush(searchingOrder(client, 5000));
        }

        assertEquals(count, orderRepository.count());
    }

    @CsvSource({
            "ALFA_TEAM,ONLY_PAYS,8f1c2d3e",
            "ONLY_PAYS,ALFA_TEAM,c4b5a6d7"
    })
    @ParameterizedTest
    @DisplayName("Ордера разных мерчантов с одинаковым идентификатором ордера мерчанта должны сохраняться.")
    void save_shouldSaveSameMerchantOrderIdForDifferentMerchants(Merchant first, Merchant second,
                                                                 String merchantOrderId) {
        Client client = getDummyClient();

        orderRepository.saveAndFlush(merchantOrder(client, first, merchantOrderId));
        orderRepository.saveAndFlush(merchantOrder(client, second, merchantOrderId));

        assertEquals(2, orderRepository.count());
    }

    @CsvSource({
            "ALFA_TEAM,8f1c2d3e",
            "ONLY_PAYS,c4b5a6d7"
    })
    @ParameterizedTest
    @DisplayName("Должен быть брошен DataIntegrityViolationException, если у мерчанта уже есть ордер с таким же идентификатором.")
    void save_shouldThrowIfMerchantOrderIdDuplicated(Merchant merchant, String merchantOrderId) {
        Client client = getDummyClient();
        orderRepository.saveAndFlush(merchantOrder(client, merchant, merchantOrderId));
        Order duplicate = merchantOrder(client, merchant, merchantOrderId);

        assertThrows(DataIntegrityViolationException.class, () -> orderRepository.saveAndFlush(duplicate));
    }

    @CsvSource({
            "a1f3c2d4,1000,https://example.com/callback",
            "b7e9d0c1,7300,https://google.com/callback"
    })
    @ParameterizedTest
    @DisplayName("Метод должен сохранить ордер в статусе SEARCHING с переданными данными.")
    void createOrder_shouldSaveSearchingOrder(String internalId, int amount, String callbackUrl) {
        Client client = getDummyClient();

        Order order = orderService.createOrder(client, internalId, amount, callbackUrl);

        Order actual = orderRepository.findById(order.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(OrderStatus.SEARCHING, actual.getStatus()),
                () -> assertEquals(client.getId(), actual.getClient().getId()),
                () -> assertEquals(internalId, actual.getInternalId()),
                () -> assertEquals(amount, actual.getAmount()),
                () -> assertEquals(callbackUrl, actual.getCallbackUrl()),
                () -> assertNotNull(actual.getCreatedAt()),
                () -> assertNull(actual.getExpiresAt()),
                () -> assertNull(actual.getDetails())
        );
    }

    @CsvSource({
            "c4d2e8f1,1000",
            "d9a7b3e5,7300"
    })
    @ParameterizedTest
    @DisplayName("Метод должен бросить BadRequestException и не сохранять ордер, если ордер с таким internalId уже существует.")
    void createOrder_shouldThrowIfInternalIdNotUnique(String internalId, int amount) {
        Client client = getDummyClient();
        orderService.createOrder(client, internalId, amount, null);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> orderService.createOrder(client, internalId, amount, null));

        assertAll(
                () -> assertEquals(ExceptionMessage.INTERNAL_ID_NOT_UNIQUE, exception.getMessage()),
                () -> assertEquals(1, orderRepository.findAll().stream()
                        .filter(order -> internalId.equals(order.getInternalId())).count())
        );
    }

    @ValueSource(strings = {
            "https://example.com/client-callback",
            "https://google.com/client-callback"
    })
    @ParameterizedTest
    @DisplayName("Метод должен сохранить ордер с callback url клиента, если он не передан.")
    void createOrder_shouldUseClientCallbackUrlIfNotPassed(String clientCallbackUrl) {
        Client client = getDummyClient();
        client.setCallbackUrl(clientCallbackUrl);

        Order order = orderService.createOrder(client, UUID.randomUUID().toString(), 5000, null);

        assertEquals(clientCallbackUrl, orderRepository.findById(order.getId()).orElseThrow().getCallbackUrl());
    }

    @CsvSource({
            "NEW,Т-Банк",
            "DETAILS_NOT_FOUND,Сбербанк"
    })
    @ParameterizedTest
    @DisplayName("Метод должен вернуть проекцию ордера, если ордер принадлежит клиенту.")
    void findByIdAndClientId_shouldReturnProjectionOfClientOrder(OrderStatus status, String bank) {
        Order order = saveOrder(status, "OLD");
        order.setBank(bank);
        orderRepository.saveAndFlush(order);

        ClientOrderSummary actual = orderService.findByIdAndClientId(order.getId(), order.getClient().getId(),
                ClientOrderSummary.class).orElseThrow();

        assertAll(
                () -> assertEquals(order.getId(), actual.id()),
                () -> assertEquals(status, actual.status()),
                () -> assertEquals(bank, actual.bank()),
                () -> assertEquals(order.getInternalId(), actual.internalId())
        );
    }

    @ValueSource(strings = {
            "2f6c1d9e-8a10-4b7e-9d05-6a4f9c3b2e71",
            "c3b2e71a-1c2f-4f0e-8d05-7b0f1c524f3e"
    })
    @ParameterizedTest
    @DisplayName("Метод должен вернуть пустой Optional, если ордер принадлежит другому клиенту.")
    void findByIdAndClientId_shouldReturnEmptyIfOrderOfAnotherClient(UUID anotherClientId) {
        Order order = getDummyOrder(getDummyClient());

        assertTrue(orderService.findByIdAndClientId(order.getId(), anotherClientId, ClientOrderSummary.class)
                .isEmpty());
    }

    @EnumSource(value = OrderStatus.class, names = {"SEARCHING", "NEW"})
    @ParameterizedTest
    @DisplayName("Ордер должен сохраняться без URL для колбэков.")
    void save_shouldSaveOrderWithoutCallbackUrl(OrderStatus status) {
        Order order = searchingOrder(getDummyClient(), 5000);
        order.setStatus(status);
        order.setCallbackUrl(null);

        orderRepository.saveAndFlush(order);

        assertNull(orderRepository.findById(order.getId()).orElseThrow().getCallbackUrl());
    }

    private Order searchingOrder(Client client, int amount) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCreatedAt(Instant.now());
        order.setClient(client);
        order.setInternalId(UUID.randomUUID().toString());
        order.setStatus(OrderStatus.SEARCHING);
        order.setAmount(amount);
        order.setCallbackUrl("https://google.com/callback");
        return order;
    }

    private Order merchantOrder(Client client, Merchant merchant, String merchantOrderId) {
        Order order = new Order();
        order.setMerchant(merchant);
        order.setMerchantOrderId(merchantOrderId);
        fillFields(order, client);
        return order;
    }
}
