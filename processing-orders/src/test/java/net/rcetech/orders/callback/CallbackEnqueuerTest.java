package net.rcetech.orders.callback;

import net.rcetech.domain.config.IncidentConfig;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.ClientCallback;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.ClientCallbackRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.domain.service.orders.ClientCallbackService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.domain.service.support.SupportUserService;
import net.rcetech.domain.test.SharedMySQLContainer;
import net.rcetech.meta.orders.ClientCallbackStatus;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChangeSource;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.orders.config.OrdersWebConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tgb.cryptoexchange.commons.enums.Merchant;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@DataJpaTest
@Import({CallbackEnqueuer.class, OrderService.class, ClientCallbackService.class, OrdersWebConfig.class,
        IncidentService.class, SupportUserService.class, IncidentConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CallbackEnqueuerTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SharedMySQLContainer.register(registry);
    }

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientCallbackRepository clientCallbackRepository;

    @Autowired
    private CallbackEnqueuer callbackEnqueuer;

    @MockitoSpyBean(name = "callbackMapper")
    private ObjectMapper callbackMapper;

    @Autowired
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        reset(callbackMapper);
    }

    @ParameterizedTest
    @CsvSource({
            "SUCCESS,serialization failed",
            "CANCELED,payload is broken"
    })
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Ошибка постановки КБ в очередь должна откатывать смену статуса ордера.")
    void updateStatus_shouldRollbackStatusIfEnqueueFailed(OrderStatus status, String error) {
        Order order = saveOrder(OrderStatus.NEW, 5000, "https://example.com/callback");
        doThrow(new IllegalStateException(error)).when(callbackMapper).writeValueAsString(any());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> orderService.updateStatus(
                order.getId(), status, OrderStatusChangeSource.SYSTEM, true, changed -> { }));

        assertEquals(error, exception.getMessage());
        assertEquals(OrderStatus.NEW, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertTrue(findCallbacks(order.getId()).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "SUCCESS,5000,https://example.com/callback",
            "CANCELED,1200,https://client.org/cb"
    })
    @DisplayName("Событие смены статуса должно поставить в очередь КБ с текущим состоянием ордера.")
    void enqueueCallback_shouldCreatePendingCallbackWithOrderSnapshot(OrderStatus status, int amount, String url) {
        Order order = saveOrder(status, amount, url);

        callbackEnqueuer.enqueueCallback(event(order, true));

        List<ClientCallback> callbacks = findCallbacks(order.getId());
        assertEquals(1, callbacks.size());
        ClientCallback callback = callbacks.getFirst();
        JsonNode payload = callbackMapper.readTree(callback.getPayload());
        assertAll(
                () -> assertEquals(ClientCallbackStatus.PENDING, callback.getStatus()),
                () -> assertEquals(status, callback.getOrderStatus()),
                () -> assertEquals(url, callback.getUrl()),
                () -> assertEquals(0, callback.getAttempts()),
                () -> assertNotNull(callback.getNextAttemptAt()),
                () -> assertNull(callback.getSentAt()),
                () -> assertEquals(order.getId().toString(), payload.get("id").asString()),
                () -> assertEquals(status.name(), payload.get("status").asString()),
                () -> assertEquals(amount, payload.get("amount").asInt()),
                () -> assertEquals(url, payload.get("callbackUrl").asString())
        );
    }

    @ParameterizedTest
    @CsvSource({
            "NEW;CANCELED;SUCCESS",
            "NEW;DISPUTE;CANCELED;DISPUTE;SUCCESS"
    })
    @DisplayName("КБ должны ставиться в очередь в порядке смены статусов, каждый со своим статусом.")
    void enqueueCallback_shouldKeepStatusOrder(String statuses) {
        List<OrderStatus> expected = Arrays.stream(statuses.split(";")).map(OrderStatus::valueOf).toList();
        Order order = saveOrder(expected.getFirst(), 5000, "https://example.com/callback");

        for (OrderStatus status : expected) {
            order.setStatus(status);
            orderRepository.save(order);
            callbackEnqueuer.enqueueCallback(event(order, true));
        }

        List<OrderStatus> actual = findCallbacks(order.getId()).stream()
                .map(callback -> OrderStatus.valueOf(callbackMapper.readTree(callback.getPayload())
                        .get("status").asString()))
                .toList();
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"DETAILS_NOT_FOUND", "NEW"})
    @DisplayName("КБ не должен ставиться в очередь, если в событии указано не уведомлять клиента.")
    void enqueueCallback_shouldSkipIfNotifyClientFalse(OrderStatus status) {
        Order order = saveOrder(status, 5000, "https://example.com/callback");

        callbackEnqueuer.enqueueCallback(event(order, false));

        assertTrue(findCallbacks(order.getId()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {700, 9000})
    @DisplayName("КБ не должен ставиться в очередь, если у ордера нет адреса КБ.")
    void enqueueCallback_shouldSkipIfNoCallbackUrl(int amount) {
        Order order = saveOrder(OrderStatus.SUCCESS, amount, null);

        callbackEnqueuer.enqueueCallback(event(order, true));

        assertTrue(findCallbacks(order.getId()).isEmpty());
    }

    private OrderStatusUpdatedEvent event(Order order, boolean notifyClient) {
        return new OrderStatusUpdatedEvent(order.getId(), order.getStatus(), OrderStatusChangeSource.SYSTEM,
                notifyClient);
    }

    private Order saveOrder(OrderStatus status, int amount, String callbackUrl) {
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
        order.setAmount(amount);
        order.setMerchant(Merchant.ALFA_TEAM);
        order.setMerchantOrderId(UUID.randomUUID().toString());
        order.setMethod(RequestMethod.CARD);
        order.setDetails("1234 1234 1234 1234");
        order.setBank("ALFA");
        order.setCallbackUrl(callbackUrl);
        return orderRepository.save(order);
    }

    private List<ClientCallback> findCallbacks(UUID orderId) {
        return clientCallbackRepository.findAll().stream()
                .filter(callback -> orderId.equals(callback.getOrderId()))
                .sorted(Comparator.comparing(ClientCallback::getId))
                .toList();
    }
}
