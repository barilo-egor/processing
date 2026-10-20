package net.rcetech.orders;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.incidents.IncidentType;
import net.rcetech.meta.incidents.dto.StuckSearchOrderErrorData;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChangeSource;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({OrderSearchTimeoutProcessor.class, OrderFacade.class, OrderService.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@RecordApplicationEvents
class OrderSearchTimeoutProcessorTest {

    @Container
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.0.46");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:database/migration");
    }

    @MockitoBean
    private IncidentService incidentService;

    @MockitoSpyBean
    private OrderFacade orderFacade;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderSearchTimeoutProcessor orderSearchTimeoutProcessor;

    @Autowired
    private ApplicationEvents applicationEvents;

    Client getDummyClient() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test" + client.getId());
        client.setRegisteredAt(Instant.now());
        return clientRepository.save(client);
    }

    Order saveOrder(Client client, OrderStatus status, long ageSeconds) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCreatedAt(Instant.now().minusSeconds(ageSeconds));
        order.setClient(client);
        order.setInternalId(UUID.randomUUID().toString());
        order.setStatus(status);
        order.setAmount(5000);
        order.setCallbackUrl("https://google.com/callback");
        return orderRepository.save(order);
    }

    OrderStatus statusOf(Order order) {
        return orderRepository.findById(order.getId()).orElseThrow().getStatus();
    }

    @ParameterizedTest
    @ValueSource(longs = {335, 3600})
    @DisplayName("Ордер в поиске реквизитов дольше 330 секунд должен перейти в DETAILS_NOT_FOUND с КБ клиенту.")
    void checkStuckInSearchOrders_shouldMoveStuckOrderToDetailsNotFound(long ageSeconds) {
        Order order = saveOrder(getDummyClient(), OrderStatus.SEARCHING, ageSeconds);

        orderSearchTimeoutProcessor.checkStuckInSearchOrders();

        assertEquals(OrderStatus.DETAILS_NOT_FOUND, statusOf(order));
        assertEquals(List.of(new OrderStatusUpdatedEvent(order.getId(), OrderStatus.DETAILS_NOT_FOUND,
                        OrderStatusChangeSource.SYSTEM, true)),
                applicationEvents.stream(OrderStatusUpdatedEvent.class).toList());
        verifyNoInteractions(incidentService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 325})
    @DisplayName("Ордер в поиске реквизитов не дольше 330 секунд не должен меняться.")
    void checkStuckInSearchOrders_shouldNotChangeOrderIfSearchNotStuck(long ageSeconds) {
        Order order = saveOrder(getDummyClient(), OrderStatus.SEARCHING, ageSeconds);

        orderSearchTimeoutProcessor.checkStuckInSearchOrders();

        assertEquals(OrderStatus.SEARCHING, statusOf(order));
        assertEquals(0, applicationEvents.stream(OrderStatusUpdatedEvent.class).count());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CANCELED", "DETAILS_NOT_FOUND", "SUCCESS", "DISPUTE"})
    @DisplayName("Старые ордера не в поиске реквизитов не должны меняться.")
    void checkStuckInSearchOrders_shouldNotChangeOrderNotInSearch(OrderStatus status) {
        Order order = saveOrder(getDummyClient(), status, 3600);

        orderSearchTimeoutProcessor.checkStuckInSearchOrders();

        assertEquals(status, statusOf(order));
        assertEquals(0, applicationEvents.stream(OrderStatusUpdatedEvent.class).count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Deadlock found", "Connection is not available"})
    @DisplayName("Ошибка по одному зависшему ордеру не должна мешать обработке остальных.")
    void checkStuckInSearchOrders_shouldProcessOtherOrdersIfOneFailed(String message) {
        Client client = getDummyClient();
        Order failed = saveOrder(client, OrderStatus.SEARCHING, 400);
        Order other = saveOrder(client, OrderStatus.SEARCHING, 500);
        doThrow(new IllegalStateException(message)).when(orderFacade)
                .routeOrder(eq(OrderStatus.DETAILS_NOT_FOUND), eq(failed.getId()), any(), any());

        orderSearchTimeoutProcessor.checkStuckInSearchOrders();

        assertEquals(OrderStatus.SEARCHING, statusOf(failed));
        assertEquals(OrderStatus.DETAILS_NOT_FOUND, statusOf(other));
        verify(incidentService).save(eq(IncidentType.STUCK_SEARCH_ORDER_ERROR),
                eq(new StuckSearchOrderErrorData(failed.getId(), message)), anyLong());
        verifyNoMoreInteractions(incidentService);
    }

    @ParameterizedTest
    @CsvSource({
            "60,95,85",
            "120,155,145"
    })
    @DisplayName("Порог зависания должен считаться от максимального таймаута поиска из настроек плюс 30 секунд.")
    void checkStuckInSearchOrders_shouldUseMaxTimeoutFromSettings(int maxTimeoutSeconds, long stuckAgeSeconds,
                                                                   long notStuckAgeSeconds) {
        Client client = getDummyClient();
        Order stuck = saveOrder(client, OrderStatus.SEARCHING, stuckAgeSeconds);
        Order notStuck = saveOrder(client, OrderStatus.SEARCHING, notStuckAgeSeconds);
        OrderSearchTimeoutProcessor processor = new OrderSearchTimeoutProcessor(orderFacade, orderService,
                incidentService, maxTimeoutSeconds);

        processor.checkStuckInSearchOrders();

        assertEquals(OrderStatus.DETAILS_NOT_FOUND, statusOf(stuck));
        assertEquals(OrderStatus.SEARCHING, statusOf(notStuck));
        verifyNoInteractions(incidentService);
    }
}
