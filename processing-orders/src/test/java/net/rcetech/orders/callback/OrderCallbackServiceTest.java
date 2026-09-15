package net.rcetech.orders.callback;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.orders.MerchantCallbackEvent;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.orders.status.AlfaTeamOrderStatusResolver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrderService.class)
@Testcontainers
@RecordApplicationEvents
class OrderCallbackServiceTest {

    @TestConfiguration
    static class Configuration {

        @Bean
        public AlfaTeamOrderStatusResolver alfaTeamOrderStatusResolver() {
            return new AlfaTeamOrderStatusResolver();
        }

        @Bean
        public OrderCallbackService orderCallbackService(AlfaTeamOrderStatusResolver alfaTeamOrderStatusResolver,
                                                         OrderService orderService) {
            return new OrderCallbackService(List.of(alfaTeamOrderStatusResolver), orderService);
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
    private OrderCallbackService orderCallbackService;

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
        client.setStatus(ClientStatus.ACTIVE);
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
    @ValueSource(strings = {
            "12363466", "7257beb1-b01f-4c05-a053-1e1144f3d18b"
    })
    void resolve_shouldSkipIfOrderNotFound(String id) {
        Client client = getDummyClient();
        Order order = getDummyOrder(client);
        MerchantCallbackEvent event = new MerchantCallbackEvent();
        event.setMerchantOrderId(id);
        orderCallbackService.resolve(event);
        assertNotNull(order.getId());
        Optional<Order> maybeOrder = orderRepository.findById(order.getId());
        assertTrue(maybeOrder.isPresent());
        assertEquals(OrderStatus.NEW, maybeOrder.get().getStatus());
    }

    @ParameterizedTest
    @CsvSource({
            "12363466,QWERTY",
            "7257beb1-b01f-4c05-a053-1e1144f3d18b,VERY_SUCCESS"
    })
    void resolve_shouldThrowBaseExceptionIfStatusNotResolveed(String id, String status) {
        Client client = getDummyClient();
        Order order = new Order();
        order.setMerchantOrderId(id);
        fillFields(order, client);
        orderRepository.save(order);
        MerchantCallbackEvent event = new MerchantCallbackEvent();
        event.setMerchantOrderId(id);
        event.setStatus(status);
        event.setStatusDescription("Status");
        event.setMerchant(Merchant.ALFA_TEAM);
        assertThrows(BaseException.class, () -> orderCallbackService.resolve(event));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "7de063f7-720f-4078-9cde-c8d1ff924283",
            "d958fbca-5c3d-4941-8918-4c74e144d8e2"
    })
    void resolve_shouldSkipIfStatusUnprocessable(UUID id) {
        Client client = getDummyClient();
        Order order = new Order();
        order.setId(id);
        fillFields(order, client);
        orderRepository.save(order);
        MerchantCallbackEvent event = new MerchantCallbackEvent();
        event.setStatus("NEW");
        event.setStatusDescription("Status");
        event.setMerchant(Merchant.ALFA_TEAM);
        event.setMerchantOrderId(id.toString());
        orderCallbackService.resolve(event);
        assertNotNull(order.getId());
        Optional<Order> maybeOrder = orderRepository.findById(order.getId());
        assertTrue(maybeOrder.isPresent());
        assertEquals(OrderStatus.NEW, maybeOrder.get().getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "7de063f7-720f-4078-9cde-c8d1ff924283",
            "d958fbca-5c3d-4941-8918-4c74e144d8e2"
    })
    void resolve_shouldConfirmOrder(UUID id) {
        Client client = getDummyClient();
        Order order = new Order();
        order.setId(id);
        fillFields(order, client);
        orderRepository.save(order);

        MerchantCallbackEvent event = new MerchantCallbackEvent();
        event.setMerchantOrderId(id.toString());
        event.setStatus("PAID");
        event.setStatusDescription("Status");
        event.setMerchant(Merchant.ALFA_TEAM);
        orderCallbackService.resolve(event);

        assertNotNull(order.getId());
        Optional<Order> maybeOrder = orderRepository.findById(order.getId());
        assertTrue(maybeOrder.isPresent());
        assertEquals(OrderStatus.SUCCESS, maybeOrder.get().getStatus());
        List<OrderStatusUpdatedEvent> actualEvents = applicationEvents.stream(OrderStatusUpdatedEvent.class).toList();
        assertEquals(1, actualEvents.size());
        assertEquals(order.getId(), actualEvents.getFirst().getOrderId());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "7de063f7-720f-4078-9cde-c8d1ff924283",
            "d958fbca-5c3d-4941-8918-4c74e144d8e2"
    })
    void resolve_shouldCancelOrder(UUID id) {
        Client client = getDummyClient();
        Order order = new Order();
        order.setId(id);
        fillFields(order, client);
        orderRepository.save(order);

        MerchantCallbackEvent event = new MerchantCallbackEvent();
        event.setMerchantOrderId(id.toString());
        event.setStatus("CANCELED");
        event.setStatusDescription("Status");
        event.setMerchant(Merchant.ALFA_TEAM);
        orderCallbackService.resolve(event);

        assertNotNull(order.getId());
        Optional<Order> maybeOrder = orderRepository.findById(order.getId());
        assertTrue(maybeOrder.isPresent());
        assertEquals(OrderStatus.CANCELED, maybeOrder.get().getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "7de063f7-720f-4078-9cde-c8d1ff924283",
            "d958fbca-5c3d-4941-8918-4c74e144d8e2"
    })
    void resolve_shouldTimeoutOrder(UUID id) {
        Client client = getDummyClient();
        Order order = new Order();
        order.setId(id);
        fillFields(order, client);
        orderRepository.save(order);

        MerchantCallbackEvent event = new MerchantCallbackEvent();
        event.setMerchantOrderId(id.toString());
        event.setStatus("EXPIRED");
        event.setStatusDescription("Status");
        event.setMerchant(Merchant.ALFA_TEAM);
        orderCallbackService.resolve(event);

        assertNotNull(order.getId());
        Optional<Order> maybeOrder = orderRepository.findById(order.getId());
        assertTrue(maybeOrder.isPresent());
        assertEquals(OrderStatus.TIMEOUT, maybeOrder.get().getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "7de063f7-720f-4078-9cde-c8d1ff924283",
            "d958fbca-5c3d-4941-8918-4c74e144d8e2"
    })
    void resolve_shouldDisputeOrder(UUID id) {
        Client client = getDummyClient();
        Order order = new Order();
        order.setId(id);
        fillFields(order, client);
        orderRepository.save(order);

        MerchantCallbackEvent event = new MerchantCallbackEvent();
        event.setMerchantOrderId(id.toString());
        event.setStatus("DISPUTE");
        event.setStatusDescription("Status");
        event.setMerchant(Merchant.ALFA_TEAM);
        orderCallbackService.resolve(event);

        assertNotNull(order.getId());
        Optional<Order> maybeOrder = orderRepository.findById(order.getId());
        assertTrue(maybeOrder.isPresent());
        assertEquals(OrderStatus.DISPUTE, maybeOrder.get().getStatus());
    }
}