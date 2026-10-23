package net.rcetech.orders;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.domain.test.SharedMySQLContainer;
import net.rcetech.meta.incidents.OrderExpirationErrorEvent;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.RequestMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;

@DataJpaTest
@Import({OrderExpirationProcessor.class, OrderFacade.class, OrderService.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@RecordApplicationEvents
class OrderExpirationProcessorTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SharedMySQLContainer.register(registry);
    }

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderExpirationProcessor orderExpirationProcessor;

    @MockitoSpyBean
    private OrderFacade orderFacade;

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
            order.setCreatedAt(order.getExpiresAt() == null ? Instant.now() : order.getExpiresAt().minusSeconds(900));
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

    @ParameterizedTest
    @ValueSource(longs = {
            1791392491000L,
            1791393664800L
    })
    @DisplayName("Метод не должен обновлять статус в таймаут неистекшим ордерам.")
    void checkExpiredOrders_shouldNotUpdateNotExpiredOrders(long millis) {
        Client client = getDummyClient();
        for (int i = 0; i < 3; i++) {
            Order order = new Order();
            order.setExpiresAt(Instant.ofEpochMilli(millis));
            fillFields(order, client);
            orderRepository.save(order);
        }
        Instant now = Instant.ofEpochMilli(millis - 100000);
        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            mockedInstant.when(Instant::now).thenReturn(now);
            orderExpirationProcessor.checkExpiredOrders();
        }
        assertTrue(orderRepository.findAll().stream().allMatch(o -> OrderStatus.NEW.equals(o.getStatus())));
    }

    @ParameterizedTest
    @ValueSource(longs = {
            1791392491000L,
            1791393664800L
    })
    @DisplayName("Метод должен обновить статус ордеров в TIMEOUT.")
    void checkExpired_shouldUpdateStatusToTimeout(long millis) {
        Client client = getDummyClient();
        for (int i = 0; i < 3; i++) {
            Order order = new Order();
            order.setExpiresAt(Instant.ofEpochMilli(millis));
            fillFields(order, client);
            orderRepository.save(order);
        }
        Instant now = Instant.ofEpochMilli(millis + 100000);
        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            mockedInstant.when(Instant::now).thenReturn(now);
            orderExpirationProcessor.checkExpiredOrders();
        }
        assertTrue(orderRepository.findAll().stream().allMatch(o -> OrderStatus.TIMEOUT.equals(o.getStatus())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Deadlock found", "Connection is not available"})
    @DisplayName("Ошибка по одному просроченному ордеру должна создавать инцидент и не мешать обработке остальных.")
    void checkExpiredOrders_shouldProcessOtherOrdersIfOneFailed(String message) {
        Client client = getDummyClient();
        Order failed = new Order();
        failed.setExpiresAt(Instant.now().minusSeconds(60));
        fillFields(failed, client);
        orderRepository.save(failed);
        Order other = new Order();
        other.setExpiresAt(Instant.now().minusSeconds(120));
        fillFields(other, client);
        orderRepository.save(other);
        doThrow(new IllegalStateException(message)).when(orderFacade)
                .routeOrder(eq(OrderStatus.TIMEOUT), eq(failed.getId()), any(), any());
        long before = System.currentTimeMillis();

        orderExpirationProcessor.checkExpiredOrders();
        long after = System.currentTimeMillis();

        assertEquals(OrderStatus.NEW, orderRepository.findById(failed.getId()).orElseThrow().getStatus());
        assertEquals(OrderStatus.TIMEOUT, orderRepository.findById(other.getId()).orElseThrow().getStatus());
        List<OrderExpirationErrorEvent> events = applicationEvents.stream(OrderExpirationErrorEvent.class).toList();
        assertEquals(1, events.size());
        assertAll(
                () -> assertEquals(failed.getId(), events.getFirst().orderId()),
                () -> assertEquals(message, events.getFirst().error()),
                () -> assertTrue(events.getFirst().errorTimestamp() >= before
                        && events.getFirst().errorTimestamp() <= after)
        );
    }
}
