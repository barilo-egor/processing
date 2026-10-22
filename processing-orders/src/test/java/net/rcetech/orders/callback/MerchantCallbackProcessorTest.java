package net.rcetech.orders.callback;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.MerchantCallback;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.MerchantCallbackRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.domain.service.orders.MerchantCallbackService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.domain.test.SharedMySQLContainer;
import net.rcetech.meta.incidents.MerchantCallbackHandleErrorEvent;
import net.rcetech.meta.incidents.OrderStatusTransitionRejectedEvent;
import net.rcetech.meta.orders.MerchantCallbackEvent;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.orders.status.BridgePayOrderStatusResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OrderService.class, MerchantCallbackService.class})
@RecordApplicationEvents
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MerchantCallbackProcessorTest {

    @TestConfiguration
    static class Configuration {

        @Bean
        public BridgePayOrderStatusResolver bridgePayOrderStatusResolver() {
            return new BridgePayOrderStatusResolver();
        }

        @Bean
        public MerchantCallbackProcessor merchantCallbackProcessor(
                BridgePayOrderStatusResolver bridgePayOrderStatusResolver, OrderService orderService,
                MerchantCallbackService merchantCallbackService, ApplicationEventPublisher eventPublisher,
                PlatformTransactionManager transactionManager) {
            return new MerchantCallbackProcessor(List.of(bridgePayOrderStatusResolver), orderService,
                    merchantCallbackService, eventPublisher, transactionManager);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SharedMySQLContainer.register(registry);
    }

    @Autowired
    private MerchantCallbackProcessor merchantCallbackProcessor;

    @Autowired
    private MerchantCallbackService merchantCallbackService;

    @MockitoSpyBean
    private OrderService orderService;

    @Autowired
    private MerchantCallbackRepository merchantCallbackRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    void setUp() {
        Mockito.reset(orderService);
    }

    <T> T singleEvent(Class<T> eventClass) {
        List<T> events = applicationEvents.stream(eventClass).toList();
        assertEquals(1, events.size());
        return events.getFirst();
    }

    void assertNoIncidentEvents() {
        assertAll(
                () -> assertEquals(0, applicationEvents.stream(MerchantCallbackHandleErrorEvent.class).count()),
                () -> assertEquals(0, applicationEvents.stream(OrderStatusTransitionRejectedEvent.class).count())
        );
    }

    Order saveOrder(OrderStatus status, Merchant merchant, String merchantOrderId) {
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
        order.setStatus(status);
        order.setAmount(5000);
        order.setMerchant(merchant);
        order.setMerchantOrderId(merchantOrderId);
        order.setMerchantOrderStatus("OLD");
        order.setMethod(RequestMethod.CARD);
        order.setDetails("1234 1234 1234 1234");
        order.setBank("ALFA");
        order.setCallbackUrl("https://google.com/callback");
        return orderRepository.save(order);
    }

    MerchantCallback saveCallback(Merchant merchant, String merchantOrderId, String status) {
        MerchantCallbackEvent event = new MerchantCallbackEvent();
        event.setMerchant(merchant);
        event.setMerchantOrderId(merchantOrderId);
        event.setStatus(status);
        event.setStatusDescription("Status");
        return merchantCallbackService.save(event);
    }

    MerchantCallback reload(MerchantCallback callback) {
        return merchantCallbackRepository.findById(callback.getId()).orElseThrow();
    }

    UUID orderIdOf(MerchantCallback callback) {
        return Objects.isNull(callback.getOrder()) ? null : callback.getOrder().getId();
    }

    OrderStatus statusOf(Order order) {
        return orderRepository.findById(order.getId()).orElseThrow().getStatus();
    }

    @ParameterizedTest
    @CsvSource({
            "PAID,SUCCESS,ALFA_TEAM,true",
            "CANCELED,CANCELED,DEORA,false",
            "EXPIRED,TIMEOUT,ALFA_TEAM,false",
            "DISPUTE,DISPUTE,DEORA,true"
    })
    @DisplayName("Метод должен перевести ордер по КБ, привязать КБ к ордеру и отметить его обработанным.")
    void process_shouldRouteOrderAndMarkCallbackProcessed(String callbackStatus, OrderStatus expectedStatus,
                                                          Merchant merchant, boolean byOrderId) {
        String merchantOrderId = UUID.randomUUID().toString();
        Order order = saveOrder(OrderStatus.NEW, merchant, merchantOrderId);
        MerchantCallback callback = saveCallback(merchant, byOrderId ? order.getId().toString() : merchantOrderId,
                callbackStatus);

        merchantCallbackProcessor.process(order.getId());

        Order actualOrder = orderRepository.findById(order.getId()).orElseThrow();
        MerchantCallback actual = reload(callback);
        assertAll(
                () -> assertEquals(expectedStatus, actualOrder.getStatus()),
                () -> assertEquals(callbackStatus, actualOrder.getMerchantOrderStatus()),
                () -> assertEquals(order.getId(), orderIdOf(actual)),
                () -> assertNotNull(actual.getProcessedAt()),
                () -> assertEquals(1, applicationEvents.stream(OrderStatusUpdatedEvent.class).count())
        );
        assertNoIncidentEvents();
    }

    @ParameterizedTest
    @CsvSource({
            "PAID,ALFA_TEAM",
            "CANCELED,DEORA"
    })
    @DisplayName("Метод не должен обрабатывать КБ, пока ордер в поиске реквизитов.")
    void process_shouldSkipIfOrderInSearch(String callbackStatus, Merchant merchant) {
        String merchantOrderId = UUID.randomUUID().toString();
        Order order = saveOrder(OrderStatus.SEARCHING, merchant, merchantOrderId);
        MerchantCallback callback = saveCallback(merchant, merchantOrderId, callbackStatus);

        merchantCallbackProcessor.process(order.getId());

        MerchantCallback actual = reload(callback);
        assertAll(
                () -> assertEquals(OrderStatus.SEARCHING, statusOf(order)),
                () -> assertNull(orderIdOf(actual)),
                () -> assertNull(actual.getProcessedAt())
        );
        assertNoIncidentEvents();
    }

    @ParameterizedTest
    @CsvSource({
            "CANCELED,PAID,SUCCESS",
            "DISPUTE,CANCELED,CANCELED"
    })
    @DisplayName("Метод должен обработать все необработанные КБ ордера в порядке поступления.")
    void process_shouldProcessCallbacksInOrder(String firstStatus, String secondStatus, OrderStatus expectedStatus) {
        String merchantOrderId = UUID.randomUUID().toString();
        Order order = saveOrder(OrderStatus.NEW, Merchant.ALFA_TEAM, merchantOrderId);
        MerchantCallback first = saveCallback(Merchant.ALFA_TEAM, merchantOrderId, firstStatus);
        MerchantCallback second = saveCallback(Merchant.ALFA_TEAM, order.getId().toString(), secondStatus);

        merchantCallbackProcessor.process(order.getId());

        assertAll(
                () -> assertEquals(expectedStatus, statusOf(order)),
                () -> assertNotNull(reload(first).getProcessedAt()),
                () -> assertNotNull(reload(second).getProcessedAt()),
                () -> assertEquals(2, applicationEvents.stream(OrderStatusUpdatedEvent.class).count())
        );
    }

    @ParameterizedTest
    @CsvSource({
            "CANCELED,CANCELED,ALFA_TEAM",
            "DISPUTE,DISPUTE,DEORA"
    })
    @DisplayName("Метод должен создать инцидент об отклонённом переходе, отметить КБ обработанным и обработать остальные КБ.")
    void process_shouldCreateIncidentIfTransitionRejected(String rejectedStatus, OrderStatus expectedStatusTo,
                                                          Merchant merchant) {
        String merchantOrderId = UUID.randomUUID().toString();
        Order order = saveOrder(OrderStatus.NEW, merchant, merchantOrderId);
        MerchantCallback paid = saveCallback(merchant, merchantOrderId, "PAID");
        MerchantCallback rejected = saveCallback(merchant, merchantOrderId, rejectedStatus);
        MerchantCallback next = saveCallback(merchant, merchantOrderId, "PAID");
        long before = System.currentTimeMillis();

        merchantCallbackProcessor.process(order.getId());
        long after = System.currentTimeMillis();

        OrderStatusTransitionRejectedEvent data = singleEvent(OrderStatusTransitionRejectedEvent.class);
        Order actualOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertAll(
                () -> assertTrue(data.errorTimestamp() >= before && data.errorTimestamp() <= after),
                () -> assertEquals(order.getId(), data.orderId()),
                () -> assertEquals(OrderStatus.SUCCESS, data.statusFrom()),
                () -> assertEquals(expectedStatusTo, data.statusTo()),
                () -> assertEquals(merchant, data.merchant()),
                () -> assertEquals(rejectedStatus, data.merchantOrderStatus()),
                () -> assertEquals(OrderStatus.SUCCESS, actualOrder.getStatus()),
                () -> assertEquals("PAID", actualOrder.getMerchantOrderStatus()),
                () -> assertNotNull(reload(paid).getProcessedAt()),
                () -> assertNotNull(reload(rejected).getProcessedAt()),
                () -> assertEquals(order.getId(), orderIdOf(reload(rejected))),
                () -> assertNotNull(reload(next).getProcessedAt())
        );
    }

    @ParameterizedTest
    @CsvSource({
            "QWERTY,ALFA_TEAM",
            "VERY_SUCCESS,DEORA"
    })
    @DisplayName("Метод должен создать инцидент по КБ с нераспознанным статусом, отметить его обработанным и обработать остальные КБ.")
    void process_shouldCreateIncidentIfStatusNotResolved(String callbackStatus, Merchant merchant) {
        String merchantOrderId = UUID.randomUUID().toString();
        Order order = saveOrder(OrderStatus.NEW, merchant, merchantOrderId);
        MerchantCallback unknown = saveCallback(merchant, merchantOrderId, callbackStatus);
        MerchantCallback paid = saveCallback(merchant, merchantOrderId, "PAID");
        long before = System.currentTimeMillis();

        merchantCallbackProcessor.process(order.getId());
        long after = System.currentTimeMillis();

        MerchantCallbackHandleErrorEvent data = singleEvent(MerchantCallbackHandleErrorEvent.class);
        assertAll(
                () -> assertTrue(data.errorTimestamp() >= before && data.errorTimestamp() <= after),
                () -> assertEquals(merchant, data.merchant()),
                () -> assertEquals(callbackStatus, data.status()),
                () -> assertEquals("Status", data.statusDescription()),
                () -> assertEquals(merchantOrderId, data.merchantOrderId()),
                () -> assertNotNull(reload(unknown).getProcessedAt()),
                () -> assertNotNull(reload(paid).getProcessedAt()),
                () -> assertEquals(OrderStatus.SUCCESS, statusOf(order))
        );
    }

    @ParameterizedTest
    @CsvSource({
            "Connection is not available,ALFA_TEAM",
            "Deadlock found,DEORA"
    })
    @DisplayName("Технический сбой на КБ должен откатить только этот КБ, создать инцидент и не мешать обработке остальных КБ.")
    void process_shouldCreateIncidentAndContinueIfCallbackFailed(String message, Merchant merchant) {
        String merchantOrderId = UUID.randomUUID().toString();
        Order order = saveOrder(OrderStatus.NEW, merchant, merchantOrderId);
        MerchantCallback failed = saveCallback(merchant, merchantOrderId, "DISPUTE");
        MerchantCallback paid = saveCallback(merchant, merchantOrderId, "PAID");
        doThrow(new IllegalStateException(message)).when(orderService).updateStatus(eq(order.getId()),
                eq(OrderStatus.DISPUTE), any(), anyString());
        long before = System.currentTimeMillis();

        merchantCallbackProcessor.process(order.getId());
        long after = System.currentTimeMillis();

        MerchantCallbackHandleErrorEvent data = singleEvent(MerchantCallbackHandleErrorEvent.class);
        assertAll(
                () -> assertTrue(data.errorTimestamp() >= before && data.errorTimestamp() <= after),
                () -> assertEquals(merchant, data.merchant()),
                () -> assertEquals("DISPUTE", data.status()),
                () -> assertEquals(merchantOrderId, data.merchantOrderId()),
                () -> assertNull(reload(failed).getProcessedAt()),
                () -> assertNull(orderIdOf(reload(failed))),
                () -> assertNotNull(reload(paid).getProcessedAt()),
                () -> assertEquals(OrderStatus.SUCCESS, statusOf(order))
        );
    }

    @ParameterizedTest
    @CsvSource({
            "SUCCESS,PAID,ALFA_TEAM",
            "CANCELED,CANCELED,ALFA_TEAM",
            "TIMEOUT,CANCELED,DEORA",
            "DISPUTE,DISPUTE,DEORA",
            "DETAILS_NOT_FOUND,PAID,ALFA_TEAM"
    })
    @DisplayName("Метод должен пропустить переход без инцидента и отметить КБ обработанным.")
    void process_shouldSkipTransitionAndMarkProcessed(OrderStatus status, String callbackStatus, Merchant merchant) {
        String merchantOrderId = UUID.randomUUID().toString();
        Order order = saveOrder(status, merchant, merchantOrderId);
        MerchantCallback callback = saveCallback(merchant, merchantOrderId, callbackStatus);

        merchantCallbackProcessor.process(order.getId());

        Order actualOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(status, actualOrder.getStatus()),
                () -> assertEquals("OLD", actualOrder.getMerchantOrderStatus()),
                () -> assertNotNull(reload(callback).getProcessedAt()),
                () -> assertEquals(0, applicationEvents.stream(OrderStatusUpdatedEvent.class).count())
        );
        assertNoIncidentEvents();
    }

    @ParameterizedTest
    @CsvSource({
            "ALFA_TEAM,DEORA",
            "DEORA,ALFA_TEAM"
    })
    @DisplayName("Метод не должен трогать чужие и уже обработанные КБ.")
    void process_shouldIgnoreForeignAndProcessedCallbacks(Merchant merchant, Merchant otherMerchant) {
        String merchantOrderId = UUID.randomUUID().toString();
        Order order = saveOrder(OrderStatus.NEW, merchant, merchantOrderId);
        MerchantCallback otherMerchantCallback = saveCallback(otherMerchant, merchantOrderId, "PAID");
        MerchantCallback otherOrderCallback = saveCallback(merchant, UUID.randomUUID().toString(), "PAID");
        MerchantCallback processed = saveCallback(merchant, merchantOrderId, "PAID");
        Instant processedAt = Instant.parse("2026-01-01T00:00:00Z");
        processed.setProcessedAt(processedAt);
        merchantCallbackRepository.save(processed);

        merchantCallbackProcessor.process(order.getId());

        assertAll(
                () -> assertEquals(OrderStatus.NEW, statusOf(order)),
                () -> assertNull(reload(otherMerchantCallback).getProcessedAt()),
                () -> assertNull(reload(otherOrderCallback).getProcessedAt()),
                () -> assertEquals(processedAt, reload(processed).getProcessedAt()),
                () -> assertNull(orderIdOf(reload(processed)))
        );
    }

    @ParameterizedTest
    @CsvSource({
            "CANCELED,PAID",
            "TIMEOUT,DISPUTE"
    })
    @DisplayName("Метод не должен обрабатывать КБ, если у ордера нет мерчанта.")
    void process_shouldSkipIfOrderHasNoMerchant(OrderStatus status, String callbackStatus) {
        Order order = saveOrder(status, null, null);
        MerchantCallback callback = saveCallback(Merchant.ALFA_TEAM, order.getId().toString(), callbackStatus);

        merchantCallbackProcessor.process(order.getId());

        assertAll(
                () -> assertEquals(status, statusOf(order)),
                () -> assertNull(reload(callback).getProcessedAt())
        );
        assertNoIncidentEvents();
    }
}
