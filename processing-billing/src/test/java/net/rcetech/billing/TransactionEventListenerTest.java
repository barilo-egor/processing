package net.rcetech.billing;

import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.model.billing.Transaction;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.billing.TransactionRepository;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.domain.service.billing.TransactionService;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.billing.TransactionCreatedEvent;
import net.rcetech.meta.billing.TransactionType;
import net.rcetech.meta.config.MetaWebConfig;
import net.rcetech.meta.incidents.TransactionCreationErrorEvent;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChangeSource;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import net.rcetech.meta.orders.RequestMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.transaction.TestTransaction;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        TransactionService.class, OrderService.class, TransactionEventListener.class, ClientService.class,
        MetaWebConfig.class
})
@RecordApplicationEvents
class TransactionEventListenerTest {

    @Container
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.0.46");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:database/migration");
    }

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private ClientMapper clientMapper;

    Client getDummyClient() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test" + client.getId());
        client.setRegisteredAt(Instant.now());
        return clientRepository.save(client);
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

    @RepeatedTest(value = 2)
    @DisplayName("Метод должен создать транзакцию, если статус ордера SUCCESS.")
    void orderStatusUpdated_shouldCreateTransactionIfSuccessStatus() {
        Client client = getDummyClient();
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        fillFields(order, client);
        orderRepository.save(order);
        OrderStatusUpdatedEvent event = new OrderStatusUpdatedEvent(orderId, OrderStatus.SUCCESS, OrderStatusChangeSource.MERCHANT_CALLBACK, true);
        eventPublisher.publishEvent(event);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        Optional<TransactionCreatedEvent> maybeEvent = applicationEvents.stream(TransactionCreatedEvent.class).findFirst();
        assertTrue(maybeEvent.isPresent());
        TransactionCreatedEvent actualEvent = maybeEvent.get();
        Optional<Transaction> maybeTransaction = transactionRepository.findById(actualEvent.transactionId());
        assertTrue(maybeTransaction.isPresent());
        Transaction actual = maybeTransaction.get();
        assertAll(
                () -> assertEquals(client.getId(), actual.getClient().getId()),
                () -> assertEquals(Operation.CREDIT, actual.getOperation()),
                () -> assertEquals(order.getAmount(), actual.getAmount()),
                () -> assertEquals(TransactionType.ORDER_CONFIRMATION, actual.getType()),
                () -> assertEquals("Подтверждение по ордеру " + order.getId()
                        + ". Транзакция создана системой.", actual.getComment())
        );
        transactionRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "NEW", "DISPUTE"
    })
    @DisplayName("Метод должен пропустить ивент, если это не SUCCESS статус.")
    void orderStatusUpdated_shouldSkipIfStatusNotSuccess(OrderStatus status) {
        Client client = getDummyClient();
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        fillFields(order, client);
        orderRepository.save(order);
        OrderStatusUpdatedEvent event = new OrderStatusUpdatedEvent(orderId, status, OrderStatusChangeSource.MERCHANT_CALLBACK, true);
        eventPublisher.publishEvent(event);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        Optional<TransactionCreatedEvent> maybeEvent = applicationEvents.stream(TransactionCreatedEvent.class).findFirst();
        assertTrue(maybeEvent.isEmpty());
        assertEquals(0, transactionRepository.count());
    }

    @ParameterizedTest
    @CsvSource({
            "c2cbb845-7313-4106-a1a3-e9e4b328bbd9",
            "fe4a4710-9c2e-4f04-b274-06802fbf2012"
    })
    @DisplayName("Должен быть создан инцидент, если произошла ошибка при создании транзакции.")
    void orderStatusUpdated_shouldCreateIncidentIfCantCreateTransaction(UUID orderId) {
        // ордер не сохранен в БД, поэтому создание транзакции упадет
        OrderStatusUpdatedEvent event = new OrderStatusUpdatedEvent(orderId, OrderStatus.SUCCESS, OrderStatusChangeSource.MERCHANT_CALLBACK, true);
        long before = System.currentTimeMillis();
        eventPublisher.publishEvent(event);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        long after = System.currentTimeMillis();
        assertEquals(0, transactionRepository.count());
        List<TransactionCreationErrorEvent> events = applicationEvents.stream(TransactionCreationErrorEvent.class)
                .toList();
        assertEquals(1, events.size());
        TransactionCreationErrorEvent incidentEvent = events.getFirst();
        assertAll(
                () -> assertEquals(orderId, incidentEvent.orderId()),
                () -> assertEquals(OrderStatus.SUCCESS, incidentEvent.status()),
                () -> assertTrue(incidentEvent.errorTimestamp() >= before && incidentEvent.errorTimestamp() <= after)
        );
    }
}
