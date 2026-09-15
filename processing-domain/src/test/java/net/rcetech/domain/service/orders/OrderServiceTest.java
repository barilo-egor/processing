package net.rcetech.domain.service.orders;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.domain.repository.orders.OrderSpecifications;
import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.meta.orders.dto.OrderFilter;
import net.rcetech.meta.orders.dto.OrderSummary;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @TestConfiguration
    static class Configuration {

        @Bean
        public OrderService orderService(OrderRepository orderRepository) {
            return new OrderService(orderRepository);
        }
    }

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

    @Test
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
}