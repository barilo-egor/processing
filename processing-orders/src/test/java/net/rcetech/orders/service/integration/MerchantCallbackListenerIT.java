package net.rcetech.orders.service.integration;

import net.rcetech.clients.service.ClientApi;
import net.rcetech.orders.entity.Order;
import net.rcetech.orders.enums.OrderStatus;
import net.rcetech.orders.kafka.MerchantCallbackEvent;
import net.rcetech.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class MerchantCallbackListenerIT extends BaseIntegrationTest {

    @Value("${kafka.topic.merchant-details.callback}")
    private String inputTopic;

    @Value("${kafka.topic.unknown-status.receive}")
    private String unknownStatusTopic;

    @Autowired
    private KafkaTemplate<String, String> rawKafkaTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private ClientApi clientApi;

    @MockitoSpyBean
    private KafkaTemplate<String, MerchantCallbackEvent> kafkaTemplateUnknownStatus;

    private UUID orderId;

    private String merchantId;

    @BeforeEach
    void setUpData() {
        orderId = UUID.randomUUID();
        merchantId = UUID.randomUUID().toString();
        Order order = new Order();
        order.setId(orderId);
        order.setInternalId("internalId");
        order.setStatus(OrderStatus.NEW);
        order.setAmount(1000);
        order.setClientId(322L);
        order.setMerchant(Merchant.ALFA_TEAM);
        order.setMerchantOrderId(merchantId);
        order.setMerchantOrderStatus("merchantStatus");
        orderRepository.saveAndFlush(order);
    }

    @Test
    @DisplayName("Успешный сценарий: обновление статуса заказа в SUCCESS")
    void shouldUpdateOrderStatusToSuccessWhenStatusIsSuccessful() throws Exception {
        MerchantCallbackEvent event = MerchantCallbackEvent.builder()
                .merchantOrderId(merchantId)
                .merchant(Merchant.ALFA_TEAM)
                .status("CHARGED")
                .statusDescription("Payment completed successfully")
                .build();

        String jsonPayload = objectMapper.writeValueAsString(event);
        rawKafkaTemplate.send(inputTopic, merchantId, jsonPayload).get();

        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Order updatedOrder = orderRepository.findById(orderId)
                            .orElseThrow(() -> new AssertionError("Заказ пропал из БД"));
                    assertEquals(OrderStatus.SUCCESS, updatedOrder.getStatus());
                    assertEquals("CHARGED", updatedOrder.getMerchantOrderStatus());
                });
    }

    @Test
    @DisplayName("Неуспешный сценарий: обновление статуса заказа в TIMEOUT")
    void shouldUpdateOrderStatusToTimeoutWhenStatusIsFailed() throws Exception {
        MerchantCallbackEvent event = MerchantCallbackEvent.builder()
                .merchantOrderId(merchantId)
                .merchant(Merchant.ALFA_TEAM)
                .status("CANCEL")
                .statusDescription("Payment cancel or failed")
                .build();

        String jsonPayload = objectMapper.writeValueAsString(event);
        rawKafkaTemplate.send(inputTopic, merchantId, jsonPayload).get();

        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Order updatedOrder = orderRepository.findById(orderId)
                            .orElseThrow(() -> new AssertionError("Заказ пропал из БД"));
                    assertEquals(OrderStatus.TIMEOUT, updatedOrder.getStatus());
                });
    }

    @Test
    @DisplayName("Пропуск обработки: нейтральный статус не должен изменять заказ")
    void shouldNotUpdateStatusWhenStatusIsNeutral() throws Exception {
        MerchantCallbackEvent event = MerchantCallbackEvent.builder()
                .merchantOrderId(merchantId)
                .merchant(Merchant.ALFA_TEAM)
                .status("qwerty")
                .statusDescription("Payment is qwerty")
                .build();

        String jsonPayload = objectMapper.writeValueAsString(event);
        rawKafkaTemplate.send(inputTopic, merchantId, jsonPayload).get();

        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Order orderAfterCallback = orderRepository.findById(orderId).orElseThrow();
                    assertEquals(OrderStatus.NEW, orderAfterCallback.getStatus());
                });
    }

    @Test
    @DisplayName("Валидация полей: сообщение с null-полями должно игнорироваться")
    void shouldIgnoreEventAndReturnWhenRequiredFieldsAreNull() throws Exception {
        MerchantCallbackEvent invalidEvent = MerchantCallbackEvent.builder()
                .merchantOrderId(merchantId)
                .merchant(null)
                .status(null)
                .statusDescription(null)
                .build();

        String jsonPayload = objectMapper.writeValueAsString(invalidEvent);
        rawKafkaTemplate.send(inputTopic, merchantId, jsonPayload).get();

        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Order orderAfterCallback = orderRepository.findById(orderId)
                            .orElseThrow(() -> new AssertionError("Заказ пропал из БД"));
                    assertEquals(OrderStatus.NEW, orderAfterCallback.getStatus());
                });
    }

    @Test
    @DisplayName("Отправка неизвестного статуса в отдельный топик")
    void shouldSendToUnknownStatusTopicWhenStatusIsNeutral() throws Exception {
        String unknownStatus = "CUSTOM_UNKNOWN_STATUS_FROM_MERCHANT";
        MerchantCallbackEvent event = MerchantCallbackEvent.builder()
                .merchantOrderId(merchantId)
                .merchant(Merchant.ALFA_TEAM)
                .status(unknownStatus)
                .statusDescription("Some strange status")
                .build();

        String jsonPayload = objectMapper.writeValueAsString(event);
        rawKafkaTemplate.send(inputTopic, merchantId, jsonPayload).get();

        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(kafkaTemplateUnknownStatus).send(
                        eq(unknownStatusTopic),
                        any()
                ));

        Order orderAfterCallback = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.NEW, orderAfterCallback.getStatus());
    }

}
