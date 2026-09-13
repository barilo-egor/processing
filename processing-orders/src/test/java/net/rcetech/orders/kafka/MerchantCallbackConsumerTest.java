package net.rcetech.orders.kafka;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.KafkaLogErrorHandler;
import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.meta.config.MetaWebConfig;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.orders.callback.OrderCallbackService;
import net.rcetech.orders.config.OrdersCommonConfig;
import net.rcetech.orders.status.AlfaTeamOrderStatusResolver;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({OrdersCommonConfig.class, MerchantCallbackConsumer.class, MetaWebConfig.class, KafkaLogErrorHandler.class})
@ImportAutoConfiguration(KafkaAutoConfiguration.class)
class MerchantCallbackConsumerTest {

    @TestConfiguration
    static class Configuration {

        @Bean
        public OrderService orderService(OrderRepository orderRepository) {
            return new OrderService(orderRepository);
        }

        @Bean
        public AlfaTeamOrderStatusResolver alfaTeamOrderStatusResolver() {
            return new AlfaTeamOrderStatusResolver();
        }

        @Bean
        public OrderCallbackService orderCallbackService(OrderService orderService,
                                                         AlfaTeamOrderStatusResolver alfaTeamOrderStatusResolver) {
            return new OrderCallbackService(List.of(alfaTeamOrderStatusResolver), orderService);
        }

        @Bean
        public KafkaProducer<String, String> callbackProducer(KafkaProperties kafkaProperties) {
            Properties config = new Properties();
            config.put("client.id", "client1");
            config.put("bootstrap.servers", kafkaProperties.getBootstrapServers());
            config.put("acks", "all");
            return new KafkaProducer<>(config, new StringSerializer(), new StringSerializer());
        }
    }

    @Container
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.0.46");

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer("apache/kafka-native:3.8.0");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    }

    @Autowired
    private KafkaProducer<String, String> callbackProducer;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Value("${kafka.topic.merchant-details.callback}")
    private String callbackTopic;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        clientRepository.deleteAll();
    }

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

    private final String callbackJsonTemplate =
            "{" +
                    "\"merchantOrderId\":\"%s\", " +
                    "\"status\":\"%s\", " +
                    "\"statusDescription\": \"%s\"," +
                    "\"merchant\":\"%s\"" +
                    "}";

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Если статус ордера обновился до успешного, то ордер должен быть подтвержден.")
    void callback_shouldUpdateOrderToSuccessIfMerchantOrderIdPassed() throws ExecutionException, InterruptedException {
        Client client = getDummyClient();
        Order order = getDummyOrder(client);
        orderRepository.flush();
        callbackProducer.send(new ProducerRecord<>(callbackTopic,
                String.format(callbackJsonTemplate,
                        order.getMerchantOrderId(),
                        "PAID",
                        "Оплачен",
                        "ALFA_TEAM"
                )
        )).get();
        assertNotNull(order.getId());
        await().atMost(1, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Optional<Order> maybeOrder = orderRepository.findById(order.getId());
                    assertTrue(maybeOrder.isPresent());
                    Order actual  = maybeOrder.get();
                    assertAll(
                            () -> assertEquals(OrderStatus.SUCCESS, actual.getStatus()),
                            () -> assertEquals("PAID", actual.getMerchantOrderStatus())
                    );
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Если статус ордера обновился до успешного, то ордер должен быть подтвержден.")
    void callback_shouldUpdateOrderToSuccessIfIdPassed() throws ExecutionException, InterruptedException {
        Client client = getDummyClient();
        Order order = getDummyOrder(client);
        assertNotNull(order.getId());
        orderRepository.flush();
        callbackProducer.send(new ProducerRecord<>(callbackTopic,
                String.format(callbackJsonTemplate,
                        order.getId().toString(),
                        "PAID",
                        "Оплачен",
                        "ALFA_TEAM"
                )
        )).get();
        await().atMost(1, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Optional<Order> maybeOrder = orderRepository.findById(order.getId());
                    assertTrue(maybeOrder.isPresent());
                    Order actual  = maybeOrder.get();
                    assertAll(
                            () -> assertEquals(OrderStatus.SUCCESS, actual.getStatus()),
                            () -> assertEquals("PAID", actual.getMerchantOrderStatus())
                    );
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Если статус ордера обновился до успешного, то ордер должен быть подтвержден.")
    void callback_shouldUpdateOrderToTimeout() throws ExecutionException, InterruptedException {
        Client client = getDummyClient();
        Order order = getDummyOrder(client);
        orderRepository.flush();
        callbackProducer.send(new ProducerRecord<>(callbackTopic,
                String.format(callbackJsonTemplate,
                        order.getMerchantOrderId(),
                        "EXPIRED",
                        "Истек",
                        "ALFA_TEAM"
                )
        )).get();
        assertNotNull(order.getId());
        await().atMost(1, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Optional<Order> maybeOrder = orderRepository.findById(order.getId());
                    assertTrue(maybeOrder.isPresent());
                    Order actual  = maybeOrder.get();
                    assertAll(
                            () -> assertEquals(OrderStatus.TIMEOUT, actual.getStatus()),
                            () -> assertEquals("EXPIRED", actual.getMerchantOrderStatus())
                    );
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Если статус ордера обновился до успешного, то ордер должен быть подтвержден.")
    void callback_shouldUpdateOrderToCanceled() throws ExecutionException, InterruptedException {
        Client client = getDummyClient();
        Order order = getDummyOrder(client);
        orderRepository.flush();
        callbackProducer.send(new ProducerRecord<>(callbackTopic,
                String.format(callbackJsonTemplate,
                        order.getMerchantOrderId(),
                        "CANCELED",
                        "Отменен",
                        "ALFA_TEAM"
                )
        )).get();
        assertNotNull(order.getId());
        await().atMost(1, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Optional<Order> maybeOrder = orderRepository.findById(order.getId());
                    assertTrue(maybeOrder.isPresent());
                    Order actual  = maybeOrder.get();
                    assertAll(
                            () -> assertEquals(OrderStatus.CANCELED, actual.getStatus()),
                            () -> assertEquals("CANCELED", actual.getMerchantOrderStatus())
                    );
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Если статус ордера обновился до успешного, то ордер должен быть подтвержден.")
    void callback_shouldUpdateOrderToDispute() throws ExecutionException, InterruptedException {
        Client client = getDummyClient();
        Order order = getDummyOrder(client);
        orderRepository.flush();
        callbackProducer.send(new ProducerRecord<>(callbackTopic,
                String.format(callbackJsonTemplate,
                        order.getMerchantOrderId(),
                        "DISPUTE",
                        "Спор",
                        "ALFA_TEAM"
                )
        )).get();
        assertNotNull(order.getId());
        await().atMost(1, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Optional<Order> maybeOrder = orderRepository.findById(order.getId());
                    assertTrue(maybeOrder.isPresent());
                    Order actual  = maybeOrder.get();
                    assertAll(
                            () -> assertEquals(OrderStatus.DISPUTE, actual.getStatus()),
                            () -> assertEquals("DISPUTE", actual.getMerchantOrderStatus())
                    );
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Если статус ордера обновился до успешного, то ордер должен быть подтвержден.")
    void callback_shouldSkipIfNotFoundByMerchantOrderIdAndNotUUID() throws ExecutionException, InterruptedException {
        Client client = getDummyClient();
        Order order = getDummyOrder(client);
        assertNotNull(order.getId());
        orderRepository.flush();
        callbackProducer.send(new ProducerRecord<>(callbackTopic,
                String.format(callbackJsonTemplate,
                        "qwe123",
                        "PAID",
                        "Оплачен",
                        "ALFA_TEAM"
                )
        )).get();
        await().during(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Optional<Order> maybeOrder = orderRepository.findById(order.getId());
                    assertTrue(maybeOrder.isPresent());
                    Order actual  = maybeOrder.get();
                    assertAll(
                            () -> assertEquals(OrderStatus.NEW, actual.getStatus()),
                            () -> assertEquals("NEW", actual.getMerchantOrderStatus())
                    );
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Если статус ордера обновился до успешного, то ордер должен быть подтвержден.")
    void callback_shouldSkipIfStatusNotResolved() throws ExecutionException, InterruptedException {
        Client client = getDummyClient();
        Order order = getDummyOrder(client);
        assertNotNull(order.getId());
        orderRepository.flush();
        callbackProducer.send(new ProducerRecord<>(callbackTopic,
                String.format(callbackJsonTemplate,
                        order.getMerchantOrderId(),
                        "QWE",
                        "Оплачен",
                        "ALFA_TEAM"
                )
        )).get();
        await().during(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Optional<Order> maybeOrder = orderRepository.findById(order.getId());
                    assertTrue(maybeOrder.isPresent());
                    Order actual  = maybeOrder.get();
                    assertAll(
                            () -> assertEquals(OrderStatus.NEW, actual.getStatus()),
                            () -> assertEquals("NEW", actual.getMerchantOrderStatus())
                    );
                });
    }
}