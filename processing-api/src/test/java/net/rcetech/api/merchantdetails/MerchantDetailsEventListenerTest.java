package net.rcetech.api.merchantdetails;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.config.MetaExecutorSpringConfig;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChangeSource;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import net.rcetech.meta.orders.RequestMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MerchantDetailsEventListener.class, OrderService.class, MetaExecutorSpringConfig.class})
class MerchantDetailsEventListenerTest {

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
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @MockitoBean
    private ApiMerchantDetailsGrpcService apiMerchantDetailsGrpcService;

    @AfterEach
    void cleanUp() {
        orderRepository.deleteAll();
        clientRepository.deleteAll();
    }

    private Order saveOrder(Merchant merchant, String merchantOrderId, RequestMethod method) {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test" + client.getId());
        client.setRegisteredAt(Instant.now());
        clientRepository.save(client);
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCreatedAt(Instant.now());
        order.setClient(client);
        order.setInternalId(UUID.randomUUID().toString());
        order.setStatus(OrderStatus.CANCELED);
        order.setAmount(5000);
        order.setMerchant(merchant);
        order.setMerchantOrderId(merchantOrderId);
        order.setMethod(method);
        return orderRepository.save(order);
    }

    private void publishAndCommit(UUID orderId, OrderStatus status, OrderStatusChangeSource source) {
        eventPublisher.publishEvent(new OrderStatusUpdatedEvent(orderId, status, source, true));
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    @ParameterizedTest
    @CsvSource({
            "ALFA_TEAM,a8f1c2d4-5e6b-4c7a-9d8e-1f2a3b4c5d6e,CARD,CLIENT",
            "PAYSCROW,58213,SBP,SUPPORT",
            "STORM_TRADE,10432,CARD,SYSTEM"
    })
    @DisplayName("После коммита отмены не по КБ мерчанта должна быть вызвана отмена ордера в merchant-details с данными ордера.")
    void orderStatusUpdated_shouldCancelOrderInMerchantDetailsAfterCommit(Merchant merchant, String merchantOrderId,
                                                                         RequestMethod method,
                                                                         OrderStatusChangeSource source) {
        Order order = saveOrder(merchant, merchantOrderId, method);
        eventPublisher.publishEvent(new OrderStatusUpdatedEvent(order.getId(), OrderStatus.CANCELED, source, true));
        verifyNoInteractions(apiMerchantDetailsGrpcService);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verify(apiMerchantDetailsGrpcService, timeout(5000)).cancelOrder(new CancelOrderDTO(merchant, merchantOrderId, method));
    }

    @ParameterizedTest
    @CsvSource({
            "STORM_TRADE,3c9e7a10-2b4d-4f6e-8a1c-0d2e4f6a8b9c,CARD",
            "ALFA_TEAM,77104,SBP"
    })
    @DisplayName("Отмена ордера в merchant-details не должна вызываться при откате транзакции.")
    void orderStatusUpdated_shouldNotCancelOrderInMerchantDetailsAfterRollback(Merchant merchant,
                                                                               String merchantOrderId,
                                                                               RequestMethod method) {
        Order order = saveOrder(merchant, merchantOrderId, method);
        eventPublisher.publishEvent(new OrderStatusUpdatedEvent(order.getId(), OrderStatus.CANCELED,
                OrderStatusChangeSource.CLIENT, true));
        TestTransaction.flagForRollback();
        TestTransaction.end();
        verify(apiMerchantDetailsGrpcService, after(500).never()).cancelOrder(any());
    }

    @ParameterizedTest
    @CsvSource({
            "PAYSCROW,e4b7d1a2-9c3f-4e8a-b6d5-2a1c0f9e8d7b,SBP",
            "STORM_TRADE,10432,CARD"
    })
    @DisplayName("Ошибка при отмене ордера в merchant-details не должна пробрасываться наружу.")
    void orderStatusUpdated_shouldNotThrowIfCancelOrderFailed(Merchant merchant, String merchantOrderId,
                                                              RequestMethod method) {
        doThrow(new BaseException("Непредвиденная ошибка: UNAVAILABLE"))
                .when(apiMerchantDetailsGrpcService).cancelOrder(any());
        Order order = saveOrder(merchant, merchantOrderId, method);
        eventPublisher.publishEvent(new OrderStatusUpdatedEvent(order.getId(), OrderStatus.CANCELED,
                OrderStatusChangeSource.SUPPORT, true));
        TestTransaction.flagForCommit();
        assertDoesNotThrow(TestTransaction::end);
        verify(apiMerchantDetailsGrpcService, timeout(5000)).cancelOrder(new CancelOrderDTO(merchant, merchantOrderId, method));
    }

    @ParameterizedTest
    @CsvSource({
            "ALFA_TEAM,a8f1c2d4-5e6b-4c7a-9d8e-1f2a3b4c5d6e,CARD",
            "PAYSCROW,58213,SBP"
    })
    @DisplayName("Отмена ордера в merchant-details не должна вызываться, если ордер отменён по КБ мерчанта.")
    void orderStatusUpdated_shouldNotCancelOrderInMerchantDetailsIfCanceledByMerchantCallback(
            Merchant merchant, String merchantOrderId, RequestMethod method) {
        Order order = saveOrder(merchant, merchantOrderId, method);
        publishAndCommit(order.getId(), OrderStatus.CANCELED, OrderStatusChangeSource.MERCHANT_CALLBACK);
        verify(apiMerchantDetailsGrpcService, after(500).never()).cancelOrder(any());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"NEW", "SUCCESS", "TIMEOUT", "DISPUTE", "DETAILS_NOT_FOUND"})
    @DisplayName("Отмена ордера в merchant-details не должна вызываться, если ордер переведён не в CANCELED.")
    void orderStatusUpdated_shouldNotCancelOrderInMerchantDetailsIfStatusNotCanceled(OrderStatus status) {
        Order order = saveOrder(Merchant.ALFA_TEAM, UUID.randomUUID().toString(), RequestMethod.CARD);
        publishAndCommit(order.getId(), status, OrderStatusChangeSource.SUPPORT);
        verify(apiMerchantDetailsGrpcService, after(500).never()).cancelOrder(any());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "null,null,null",
            "ALFA_TEAM,null,CARD",
            "null,58213,SBP"
    }, nullValues = "null")
    @DisplayName("Отмена ордера в merchant-details не должна вызываться, если мерчант или идентификатор ордера мерчанта не заданы.")
    void orderStatusUpdated_shouldNotCancelOrderInMerchantDetailsIfMerchantDataMissing(Merchant merchant,
                                                                                       String merchantOrderId,
                                                                                       RequestMethod method) {
        Order order = saveOrder(merchant, merchantOrderId, method);
        publishAndCommit(order.getId(), OrderStatus.CANCELED, OrderStatusChangeSource.CLIENT);
        verify(apiMerchantDetailsGrpcService, after(500).never()).cancelOrder(any());
    }

    @ParameterizedTest
    @CsvSource({
            "ALFA_TEAM,TEST_a8f1c2d4-5e6b-4c7a-9d8e-1f2a3b4c5d6e,CARD",
            "PAYSCROW,TEST_58213,SBP"
    })
    @DisplayName("Отмена тестового ордера в merchant-details не должна вызываться.")
    void orderStatusUpdated_shouldNotCancelTestOrderInMerchantDetails(Merchant merchant, String merchantOrderId,
                                                                      RequestMethod method) {
        Order order = saveOrder(merchant, merchantOrderId, method);
        publishAndCommit(order.getId(), OrderStatus.CANCELED, OrderStatusChangeSource.CLIENT);
        verify(apiMerchantDetailsGrpcService, after(500).never()).cancelOrder(any());
    }

    @ParameterizedTest
    @CsvSource({
            "ALFA_TEAM,a8f1c2d4-5e6b-4c7a-9d8e-1f2a3b4c5d6e,CARD",
            "PAYSCROW,58213,SBP"
    })
    @DisplayName("Отмена ордера в merchant-details должна вызываться в отдельном потоке.")
    void orderStatusUpdated_shouldCancelOrderInMerchantDetailsAsync(Merchant merchant, String merchantOrderId,
                                                                    RequestMethod method) {
        AtomicReference<Thread> cancelThread = new AtomicReference<>();
        doAnswer(invocation -> {
            cancelThread.set(Thread.currentThread());
            return null;
        }).when(apiMerchantDetailsGrpcService).cancelOrder(any());
        Order order = saveOrder(merchant, merchantOrderId, method);
        publishAndCommit(order.getId(), OrderStatus.CANCELED, OrderStatusChangeSource.CLIENT);
        verify(apiMerchantDetailsGrpcService, timeout(5000)).cancelOrder(new CancelOrderDTO(merchant, merchantOrderId, method));
        assertNotEquals(Thread.currentThread(), cancelThread.get());
    }

    @ParameterizedTest
    @EnumSource(OrderStatusChangeSource.class)
    @DisplayName("После коммита отмены ордера из любого источника должен отменяться поиск реквизитов по нему.")
    void cancelDetailsSearch_shouldCancelSearchAfterCommit(OrderStatusChangeSource source) {
        UUID orderId = UUID.randomUUID();
        eventPublisher.publishEvent(new OrderStatusUpdatedEvent(orderId, OrderStatus.CANCELED, source, true));
        verify(apiMerchantDetailsGrpcService, never()).cancelSearch(any());
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verify(apiMerchantDetailsGrpcService).cancelSearch(orderId);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatusChangeSource.class, names = {"CLIENT", "SUPPORT"})
    @DisplayName("Поиск реквизитов не должен отменяться при откате транзакции отмены ордера.")
    void cancelDetailsSearch_shouldNotCancelSearchAfterRollback(OrderStatusChangeSource source) {
        eventPublisher.publishEvent(new OrderStatusUpdatedEvent(UUID.randomUUID(), OrderStatus.CANCELED, source,
                true));
        TestTransaction.flagForRollback();
        TestTransaction.end();
        verify(apiMerchantDetailsGrpcService, never()).cancelSearch(any());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"NEW", "SUCCESS", "TIMEOUT", "DISPUTE", "DETAILS_NOT_FOUND"})
    @DisplayName("Поиск реквизитов не должен отменяться, если ордер переведён не в CANCELED.")
    void cancelDetailsSearch_shouldNotCancelSearchIfStatusNotCanceled(OrderStatus status) {
        publishAndCommit(UUID.randomUUID(), status, OrderStatusChangeSource.SYSTEM);
        verify(apiMerchantDetailsGrpcService, never()).cancelSearch(any());
    }
}
