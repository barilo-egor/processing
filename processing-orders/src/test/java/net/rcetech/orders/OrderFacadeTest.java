package net.rcetech.orders;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.config.MetaExecutorSpringConfig;
import net.rcetech.meta.exception.OrderStatusUpdateException;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import net.rcetech.meta.orders.RequestMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OrderFacade.class, OrderService.class, MetaExecutorSpringConfig.class, OrderFacadeTest.Configuration.class})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderFacadeTest {

    @TestConfiguration
    static class Configuration {

        @Bean
        public OrderEventsRecorder orderEventsRecorder() {
            return new OrderEventsRecorder();
        }
    }

    static class OrderEventsRecorder {

        private final AtomicReference<Runnable> onFirstUpdate = new AtomicReference<>();

        private final List<OrderStatus> committedStatuses = new CopyOnWriteArrayList<>();

        @EventListener
        public void beforeCommit(OrderStatusUpdatedEvent event) {
            Runnable action = onFirstUpdate.getAndSet(null);
            if (action != null) {
                action.run();
            }
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void afterCommit(OrderStatusUpdatedEvent event) {
            committedStatuses.add(event.orderStatus());
        }

        void reset() {
            onFirstUpdate.set(null);
            committedStatuses.clear();
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

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private OrderEventsRecorder orderEventsRecorder;

    @BeforeEach
    void setUp() {
        orderEventsRecorder.reset();
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        clientRepository.deleteAll();
    }

    Order createNewOrder() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test" + client.getId());
        client.setRegisteredAt(Instant.now());
        clientRepository.save(client);
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
        order.setMerchantOrderStatus("NEW");
        order.setMethod(RequestMethod.CARD);
        order.setDetails("1234 1234 1234 1234");
        order.setBank("ALFA");
        order.setCallbackUrl("https://google.com/callback");
        return orderRepository.save(order);
    }

    void runConcurrently(Order order, OrderStatus concurrentStatus, String concurrentMerchantStatus) {
        orderEventsRecorder.onFirstUpdate.set(() -> {
            AtomicReference<Throwable> error = new AtomicReference<>();
            Thread concurrent = Thread.ofPlatform().start(() -> {
                try {
                    orderFacade.routeOrder(concurrentStatus, order, concurrentMerchantStatus);
                } catch (Throwable e) {
                    error.set(e);
                }
            });
            try {
                concurrent.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            assertNull(error.get());
        });
    }

    List<OrderStatus> parseStatuses(String statuses) {
        return Arrays.stream(statuses.split("\\|")).map(OrderStatus::valueOf).toList();
    }

    @ParameterizedTest
    @CsvSource({
            "SUCCESS,PAID,SUCCESS,PAID_AGAIN,SUCCESS,SUCCESS,PAID",
            "CANCELED,CANCELED,CANCELED,DECLINED,CANCELED,CANCELED,CANCELED",
            "DISPUTE,DISPUTE,DISPUTE,ARBITRATION,DISPUTE,DISPUTE,DISPUTE",
            "TIMEOUT,EXPIRED,TIMEOUT,OUTDATED,TIMEOUT,TIMEOUT,EXPIRED",
            "CANCELED,CANCELED,SUCCESS,PAID,SUCCESS,CANCELED|SUCCESS,PAID",
            "TIMEOUT,EXPIRED,SUCCESS,PAID,SUCCESS,TIMEOUT|SUCCESS,PAID",
            "DISPUTE,DISPUTE,CANCELED,DECLINED,CANCELED,DISPUTE|CANCELED,DECLINED",
            "TIMEOUT,EXPIRED,CANCELED,DECLINED,TIMEOUT,TIMEOUT,EXPIRED"
    })
    @DisplayName("Метод должен повторить переход после OptimisticLock и применить таблицу переходов к статусу, установленному конкурентным КБ.")
    void routeOrder_shouldRetryAfterConcurrentUpdate(OrderStatus concurrentStatus, String concurrentMerchantStatus,
                                                     OrderStatus status, String merchantStatus,
                                                     OrderStatus expectedStatus, String expectedEvents,
                                                     String expectedMerchantStatus) {
        Order order = createNewOrder();
        runConcurrently(order, concurrentStatus, concurrentMerchantStatus);

        assertDoesNotThrow(() -> orderFacade.routeOrder(status, order, merchantStatus));

        Order actual = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(expectedStatus, actual.getStatus());
        assertEquals(expectedMerchantStatus, actual.getMerchantOrderStatus());
        assertEquals(parseStatuses(expectedEvents), orderEventsRecorder.committedStatuses);
    }

    @ParameterizedTest
    @CsvSource({
            "CANCELED,DECLINED",
            "DISPUTE,ARBITRATION"
    })
    @DisplayName("Метод должен выбросить OrderStatusUpdateException после повтора, если ордер был подтверждён конкурентным КБ.")
    void routeOrder_shouldRejectAfterConcurrentConfirmation(OrderStatus status, String merchantStatus) {
        Order order = createNewOrder();
        runConcurrently(order, OrderStatus.SUCCESS, "PAID");

        OrderStatusUpdateException exception = assertThrows(OrderStatusUpdateException.class,
                () -> orderFacade.routeOrder(status, order, merchantStatus));

        assertEquals(OrderStatus.SUCCESS, exception.getStatusFrom());
        assertEquals(status, exception.getStatusTo());
        Order actual = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.SUCCESS, actual.getStatus());
        assertEquals("PAID", actual.getMerchantOrderStatus());
        assertEquals(List.of(OrderStatus.SUCCESS), orderEventsRecorder.committedStatuses);
    }

    @ParameterizedTest
    @CsvSource({
            "PROCESSING",
            "WAITING_FOR_PAYMENT"
    })
    @DisplayName("Метод должен проигнорировать статус NEW без изменения ордера.")
    void routeOrder_shouldIgnoreNewStatus(String merchantOrderStatus) {
        Order order = createNewOrder();

        orderFacade.routeOrder(OrderStatus.NEW, order, merchantOrderStatus);

        Order actual = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.NEW, actual.getStatus());
        assertEquals("NEW", actual.getMerchantOrderStatus());
        assertTrue(orderEventsRecorder.committedStatuses.isEmpty());
    }
}
