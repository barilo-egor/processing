package net.rcetech.orders;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.domain.service.orders.OrderService;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

@DataJpaTest
@Import({OrderExpirationProcessor.class, OrderFacade.class, OrderService.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderExpirationProcessorTest {

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
    private ClientRepository clientRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderExpirationProcessor orderExpirationProcessor;

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
        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class)) {
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
        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class)) {
            mockedInstant.when(Instant::now).thenReturn(now);
            orderExpirationProcessor.checkExpiredOrders();
        }
        assertTrue(orderRepository.findAll().stream().allMatch(o -> OrderStatus.TIMEOUT.equals(o.getStatus())));
    }
}