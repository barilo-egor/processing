package net.rcetech.orders.callback;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.ClientCallback;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.ClientCallbackRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.domain.service.orders.ClientCallbackService;
import net.rcetech.domain.test.SharedMySQLContainer;
import net.rcetech.domain.config.IncidentConfig;
import net.rcetech.domain.model.incidents.Incident;
import net.rcetech.domain.repository.incidents.IncidentRepository;
import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.domain.service.support.SupportUserService;
import net.rcetech.meta.incidents.IncidentType;
import net.rcetech.meta.orders.ClientCallbackStatus;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.orders.config.OrdersWebConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.wiremock.spring.EnableWireMock;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@DataJpaTest
@Import({CallbackDispatcher.class, ClientCallbackService.class, CallbackHttpSender.class, OrdersWebConfig.class,
        IncidentService.class, SupportUserService.class, IncidentConfig.class, CallbackDispatcherTest.Configuration.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnableWireMock
class CallbackDispatcherTest {

    private static final Duration TOLERANCE = Duration.ofSeconds(1);

    @TestConfiguration
    static class Configuration {

        @Bean
        Executor virtualTaskExecutor() {
            SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
            executor.setVirtualThreads(true);
            return executor;
        }
    }

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

    @MockitoSpyBean
    private ClientCallbackService clientCallbackService;

    @MockitoSpyBean
    private IncidentService incidentService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private CallbackDispatcher callbackDispatcher;

    @Value("${wiremock.server.baseUrl}")
    private String wireMockUrl;

    @BeforeEach
    void setUp() {
        reset(clientCallbackService, incidentService);
        clientCallbackRepository.deleteAll();
    }

    @ParameterizedTest
    @CsvSource({
            "200,{\"status\":\"SUCCESS\"}",
            "204,{\"status\":\"CANCELED\"}"
    })
    @DisplayName("КБ должен быть отправлен с сохранённым телом и помечен отправленным при ответе 2xx.")
    void dispatch_shouldSendPayloadAndMarkSent(int statusCode, String payload) {
        stubFor(post("/callback").willReturn(status(statusCode)));
        ClientCallback callback = clientCallbackService.create(saveOrder().getId(), OrderStatus.SUCCESS, wireMockUrl + "/callback",
                payload);

        callbackDispatcher.dispatch();

        WireMock.verify(1, postRequestedFor(urlEqualTo("/callback"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalTo(payload)));
        ClientCallback actual = clientCallbackRepository.findById(callback.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(ClientCallbackStatus.SENT, actual.getStatus()),
                () -> assertEquals(1, actual.getAttempts()),
                () -> assertNotNull(actual.getSentAt())
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 503})
    @DisplayName("После ошибки КБ должен повторяться через 10, 30 и 90 секунд, а затем помечаться недоставленным без инцидента.")
    void dispatch_shouldRetryWithBackoffAndFail(int statusCode) {
        stubFor(post("/callback").willReturn(status(statusCode)));
        ClientCallback callback = clientCallbackService.create(saveOrder().getId(), OrderStatus.SUCCESS, wireMockUrl + "/callback",
                "{}");

        for (long delaySeconds : new long[]{10, 30, 90}) {
            Instant before = Instant.now();
            callbackDispatcher.dispatch();
            Instant after = Instant.now();

            ClientCallback actual = clientCallbackRepository.findById(callback.getId()).orElseThrow();
            assertEquals(ClientCallbackStatus.PENDING, actual.getStatus());
            assertTrue(!actual.getNextAttemptAt().isBefore(before.plusSeconds(delaySeconds).minus(TOLERANCE))
                    && !actual.getNextAttemptAt().isAfter(after.plusSeconds(delaySeconds).plus(TOLERANCE)));
            makeDue(actual);
        }
        callbackDispatcher.dispatch();

        ClientCallback actual = clientCallbackRepository.findById(callback.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(ClientCallbackStatus.FAILED, actual.getStatus()),
                () -> assertEquals(4, actual.getAttempts()),
                () -> assertNull(actual.getSentAt())
        );
        WireMock.verify(4, postRequestedFor(urlEqualTo("/callback")));
        assertTrue(deliveryUnknownIncidents(callback.getOrderId()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    @DisplayName("Если КБ отправлен, но отметка об отправке не сохранилась, КБ не должен отправляться повторно.")
    void dispatch_shouldNotResendIfSentMarkNotSaved(int nextCallbacks) {
        stubFor(post("/callback").willReturn(status(200)));
        UUID orderId = saveOrder().getId();
        ClientCallback callback = clientCallbackService.create(orderId, OrderStatus.SUCCESS, wireMockUrl + "/callback", "{\"n\":0}");
        IntStream.range(0, nextCallbacks).forEach(i ->
                clientCallbackService.create(orderId, OrderStatus.SUCCESS, wireMockUrl + "/callback", "{\"n\":" + (i + 1) + "}"));
        doThrow(new IllegalStateException("db is down")).when(clientCallbackService).markSent(eq(callback.getId()));

        callbackDispatcher.dispatch();
        callbackDispatcher.dispatch();

        WireMock.verify(1, postRequestedFor(urlEqualTo("/callback")));
        assertEquals(ClientCallbackStatus.SENDING,
                clientCallbackRepository.findById(callback.getId()).orElseThrow().getStatus());
        assertTrue(deliveryUnknownIncidents(orderId).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"31,SUCCESS", "600,CANCELED"})
    @DisplayName("КБ, зависший в отправке дольше 30 секунд, должен помечаться недоставленным с инцидентом, а очередь — продолжаться.")
    void dispatch_shouldFailStuckSendingAndContinue(long secondsAgo, OrderStatus orderStatus) {
        stubFor(post("/callback").willReturn(status(200)));
        UUID orderId = saveOrder().getId();
        ClientCallback stuck = clientCallbackService.create(orderId, orderStatus, wireMockUrl + "/stuck", "{\"n\":1}");
        stuck.setStatus(ClientCallbackStatus.SENDING);
        stuck.setAttempts(1);
        stuck.setLastAttemptAt(Instant.now().minusSeconds(secondsAgo));
        clientCallbackRepository.save(stuck);
        ClientCallback next = clientCallbackService.create(orderId, OrderStatus.SUCCESS, wireMockUrl + "/callback", "{\"n\":2}");

        callbackDispatcher.dispatch();

        assertAll(
                () -> assertEquals(ClientCallbackStatus.FAILED,
                        clientCallbackRepository.findById(stuck.getId()).orElseThrow().getStatus()),
                () -> assertEquals(ClientCallbackStatus.SENT,
                        clientCallbackRepository.findById(next.getId()).orElseThrow().getStatus())
        );
        WireMock.verify(0, postRequestedFor(urlEqualTo("/stuck")));
        List<Incident> incidents = deliveryUnknownIncidents(orderId);
        assertEquals(1, incidents.size());
        assertAll(
                () -> assertEquals(orderStatus.getDescription(), incidents.getFirst().getData().get("status")),
                () -> assertEquals(wireMockUrl + "/stuck", incidents.getFirst().getData().get("url"))
        );
    }

    @ParameterizedTest
    @CsvSource({"31,NEW", "120,DISPUTE"})
    @DisplayName("Если инцидент не сохранился, зависший КБ должен остаться в отправке и обработаться при следующем запуске.")
    void dispatch_shouldKeepStuckSendingIfIncidentNotSaved(long secondsAgo, OrderStatus orderStatus) {
        UUID orderId = saveOrder().getId();
        ClientCallback stuck = clientCallbackService.create(orderId, orderStatus, wireMockUrl + "/stuck", "{}");
        stuck.setStatus(ClientCallbackStatus.SENDING);
        stuck.setAttempts(1);
        stuck.setLastAttemptAt(Instant.now().minusSeconds(secondsAgo));
        clientCallbackRepository.save(stuck);
        doThrow(new IllegalStateException("incident table is locked"))
                .when(AopTestUtils.<IncidentService>getUltimateTargetObject(incidentService))
                .saveInCurrentTransaction(eq(IncidentType.CLIENT_CALLBACK_DELIVERY_UNKNOWN), any(), anyLong());

        callbackDispatcher.dispatch();

        assertEquals(ClientCallbackStatus.SENDING,
                clientCallbackRepository.findById(stuck.getId()).orElseThrow().getStatus());
        assertTrue(deliveryUnknownIncidents(orderId).isEmpty());

        reset(incidentService);
        callbackDispatcher.dispatch();

        assertEquals(ClientCallbackStatus.FAILED,
                clientCallbackRepository.findById(stuck.getId()).orElseThrow().getStatus());
        assertEquals(1, deliveryUnknownIncidents(orderId).size());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 25})
    @DisplayName("КБ, отправляемый меньше 30 секунд, должен блокировать следующие КБ ордера.")
    void dispatch_shouldWaitForRecentSending(long secondsAgo) {
        stubFor(post("/callback").willReturn(status(200)));
        UUID orderId = saveOrder().getId();
        ClientCallback sending = clientCallbackService.create(orderId, OrderStatus.SUCCESS, wireMockUrl + "/callback", "{\"n\":1}");
        sending.setStatus(ClientCallbackStatus.SENDING);
        sending.setAttempts(1);
        sending.setLastAttemptAt(Instant.now().minusSeconds(secondsAgo));
        clientCallbackRepository.save(sending);
        ClientCallback next = clientCallbackService.create(orderId, OrderStatus.SUCCESS, wireMockUrl + "/callback", "{\"n\":2}");

        callbackDispatcher.dispatch();

        WireMock.verify(0, postRequestedFor(urlEqualTo("/callback")));
        assertAll(
                () -> assertEquals(ClientCallbackStatus.SENDING,
                        clientCallbackRepository.findById(sending.getId()).orElseThrow().getStatus()),
                () -> assertEquals(ClientCallbackStatus.PENDING,
                        clientCallbackRepository.findById(next.getId()).orElseThrow().getStatus())
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:1/callback", "http://127.0.0.1:2/cb"})
    @DisplayName("КБ должен повторяться и при недоступном сервере клиента.")
    void dispatch_shouldRetryIfServerUnavailable(String url) {
        ClientCallback callback = clientCallbackService.create(saveOrder().getId(), OrderStatus.SUCCESS, url, "{}");

        callbackDispatcher.dispatch();

        ClientCallback actual = clientCallbackRepository.findById(callback.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(ClientCallbackStatus.PENDING, actual.getStatus()),
                () -> assertEquals(1, actual.getAttempts()),
                () -> assertTrue(actual.getNextAttemptAt().isAfter(Instant.now()))
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 5})
    @DisplayName("Следующий КБ ордера не должен отправляться, пока предыдущий не доставлен.")
    void dispatch_shouldSendCallbacksOfOrderStrictlyInOrder(int count) {
        stubFor(post("/callback").willReturn(status(500)));
        UUID orderId = saveOrder().getId();
        List<String> payloads = IntStream.range(0, count).mapToObj(i -> "{\"n\":" + i + "}").toList();
        payloads.forEach(payload -> clientCallbackService.create(orderId, OrderStatus.SUCCESS, wireMockUrl + "/callback", payload));

        callbackDispatcher.dispatch();
        WireMock.verify(1, postRequestedFor(urlEqualTo("/callback")).withRequestBody(equalTo(payloads.getFirst())));
        WireMock.verify(1, postRequestedFor(urlEqualTo("/callback")));

        stubFor(post("/callback").willReturn(status(200)));
        findCallbacks(orderId).forEach(this::makeDue);
        for (int i = 0; i < count; i++) {
            callbackDispatcher.dispatch();
        }

        List<String> delivered = WireMock.getAllServeEvents().stream()
                .sorted(Comparator.comparing(event -> event.getRequest().getLoggedDate()))
                .map(ServeEvent::getRequest)
                .map(request -> request.getBodyAsString())
                .toList();
        assertEquals(payloads, delivered.subList(1, delivered.size()));
        assertTrue(findCallbacks(orderId).stream()
                .allMatch(callback -> ClientCallbackStatus.SENT.equals(callback.getStatus())));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 500})
    @DisplayName("После недоставленного КБ должен отправляться следующий КБ ордера.")
    void dispatch_shouldContinueAfterFailedCallback(int statusCode) {
        stubFor(post("/failing").willReturn(status(statusCode)));
        stubFor(post("/callback").willReturn(status(200)));
        UUID orderId = saveOrder().getId();
        ClientCallback failing = clientCallbackService.create(orderId, OrderStatus.SUCCESS, wireMockUrl + "/failing", "{\"n\":1}");
        failing.setAttempts(3);
        clientCallbackRepository.save(failing);
        ClientCallback next = clientCallbackService.create(orderId, OrderStatus.SUCCESS, wireMockUrl + "/callback", "{\"n\":2}");

        callbackDispatcher.dispatch();
        callbackDispatcher.dispatch();

        assertAll(
                () -> assertEquals(ClientCallbackStatus.FAILED,
                        clientCallbackRepository.findById(failing.getId()).orElseThrow().getStatus()),
                () -> assertEquals(ClientCallbackStatus.SENT,
                        clientCallbackRepository.findById(next.getId()).orElseThrow().getStatus())
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 4})
    @DisplayName("Первые КБ разных ордеров должны отправляться за один проход.")
    void dispatch_shouldSendHeadsOfDifferentOrdersTogether(int orders) {
        stubFor(post("/callback").willReturn(status(200)));
        List<UUID> orderIds = IntStream.range(0, orders).mapToObj(i -> saveOrder().getId()).toList();
        orderIds.forEach(orderId -> {
            clientCallbackService.create(orderId, OrderStatus.SUCCESS, wireMockUrl + "/callback", "{\"first\":true}");
            clientCallbackService.create(orderId, OrderStatus.SUCCESS, wireMockUrl + "/callback", "{\"first\":false}");
        });

        callbackDispatcher.dispatch();

        WireMock.verify(orders, postRequestedFor(urlEqualTo("/callback"))
                .withRequestBody(equalTo("{\"first\":true}")));
        WireMock.verify(orders, postRequestedFor(urlEqualTo("/callback")));
    }

    private List<Incident> deliveryUnknownIncidents(UUID orderId) {
        return incidentRepository.findAll().stream()
                .filter(incident -> IncidentType.CLIENT_CALLBACK_DELIVERY_UNKNOWN.equals(incident.getType()))
                .filter(incident -> orderId.toString().equals(incident.getData().get("orderId")))
                .toList();
    }

    private void makeDue(ClientCallback callback) {
        callback.setNextAttemptAt(Instant.now().minusSeconds(1));
        clientCallbackRepository.save(callback);
    }

    private Order saveOrder() {
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
        order.setStatus(OrderStatus.SUCCESS);
        order.setAmount(5000);
        order.setMerchant(Merchant.ALFA_TEAM);
        order.setMerchantOrderId(UUID.randomUUID().toString());
        order.setMethod(RequestMethod.CARD);
        order.setDetails("1234 1234 1234 1234");
        order.setBank("ALFA");
        return orderRepository.save(order);
    }

    private List<ClientCallback> findCallbacks(UUID orderId) {
        return clientCallbackRepository.findAll().stream()
                .filter(callback -> orderId.equals(callback.getOrderId()))
                .sorted(Comparator.comparing(ClientCallback::getId))
                .toList();
    }
}
