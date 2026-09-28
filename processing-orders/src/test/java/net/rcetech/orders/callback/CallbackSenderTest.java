package net.rcetech.orders.callback;

import com.github.tomakehurst.wiremock.client.WireMock;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.config.EnableConfiguration;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.meta.orders.dto.ClientOrderSummary;
import net.rcetech.orders.config.OrdersWebConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.wiremock.spring.EnableWireMock;
import tgb.cryptoexchange.commons.enums.Merchant;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(
        properties = {
                "app.retry.callback.multiplier=1",
                "app.retry.callback.delay=1000",
                "app.retry.callback.maxDelay=1000"
        }
)
@Import({CallbackSender.class, OrderService.class, CallbackHttpSender.class, OrdersWebConfig.class, EnableConfiguration.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@EnableWireMock
class CallbackSenderTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN, true)
            .findAndAddModules()
            .build();

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
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private CallbackSender callbackSender;

    @Value("${wiremock.server.baseUrl}")
    private String wireMockUrl;

    @Autowired
    private OrderService orderService;

    Client getDummyClient() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test" + client.getId());
        client.setRegisteredAt(Instant.now());
        return clientRepository.save(client);
    }

    Order getDummyOrder(Client client) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCreatedAt(Instant.now());
        order.setExpiresAt(Instant.now().plusSeconds(900));
        order.setClient(client);
        order.setInternalId(UUID.randomUUID().toString());
        order.setStatus(OrderStatus.SUCCESS);
        order.setAmount(5000);
        order.setMerchant(Merchant.ALFA_TEAM);
        order.setMerchantOrderId(UUID.randomUUID().toString());
        order.setMerchantOrderStatus("SUCCESS");
        order.setMethod(RequestMethod.CARD);
        order.setDetails("1234 1234 1234 1234");
        order.setBank("ALFA");
        order.setCallbackUrl(wireMockUrl + "/callback");
        return orderRepository.save(order);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 202, 203, 204})
    @Transactional
    @DisplayName("КБ должен быть отправлен один раз, если вернулся статус 20х с первой попытки.")
    void sendCallback_shouldCallHttpSenderMethod(int statusCode) {
        Order order = getDummyOrder(getDummyClient());
        Optional<ClientOrderSummary> maybeOrder = orderService.findById(order.getId(), ClientOrderSummary.class);
        assertTrue(maybeOrder.isPresent());

        String expectedJson = objectMapper.writeValueAsString(maybeOrder.get());
        stubFor(post("/callback").willReturn(status(statusCode)));

        callbackSender.sendCallback(new OrderStatusUpdatedEvent(this, order.getId(), order.getStatus()));

        WireMock.verify(1, postRequestedFor(urlEqualTo("/callback"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalTo(expectedJson)));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 500})
    @DisplayName("КБ должен отправиться 3 раза, если возвращаются ошибочные статусы.")
    void sendCallback_shouldRetryIfNot20xStatus() {
        Order order = getDummyOrder(getDummyClient());
        Optional<ClientOrderSummary> maybeOrder = orderService.findById(order.getId(), ClientOrderSummary.class);
        assertTrue(maybeOrder.isPresent());
        stubFor(post("/callback").willReturn(WireMock.status(500)));

        callbackSender.sendCallback(new OrderStatusUpdatedEvent(this, order.getId(), order.getStatus()));

        WireMock.verify(3, postRequestedFor(urlEqualTo("/callback")));
    }
}