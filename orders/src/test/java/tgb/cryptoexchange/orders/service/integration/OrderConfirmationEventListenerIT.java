package tgb.cryptoexchange.orders.service.integration;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tgb.cryptoexchange.commons.enums.Merchant;
import tgb.cryptoexchange.orders.entity.Order;
import tgb.cryptoexchange.orders.enums.OrderStatus;
import tgb.cryptoexchange.orders.kafka.OrderConfirmationEvent;
import tgb.cryptoexchange.orders.service.CallbackSender;
import tgb.cryptoexchange.orders.service.OrderService;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OrderConfirmationEventListenerIT extends BaseIntegrationTest {

    @Autowired
    private OrderService orderService;

    @MockitoBean
    private CallbackSender callbackSender;

    @Value("${kafka.topic.orders.receive}")
    private String kafkaTopic;

    private Consumer<String, OrderConfirmationEvent> testConsumer;

    @BeforeEach
    void setUpKafkaConsumer() {
        Map<String, Object> consumerProps = kafkaProperties.buildConsumerProperties();
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        StringDeserializer keyDeserializer = new StringDeserializer();
        JacksonJsonDeserializer<OrderConfirmationEvent> valueDeserializer = new JacksonJsonDeserializer<>(
                OrderConfirmationEvent.class);
        valueDeserializer.addTrustedPackages("*");

        DefaultKafkaConsumerFactory<String, OrderConfirmationEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps, keyDeserializer, valueDeserializer);

        testConsumer = consumerFactory.createConsumer();
        testConsumer.subscribe(Collections.singletonList(kafkaTopic));

        testConsumer.poll(Duration.ofMillis(300));
        testConsumer.commitSync();
    }

    @AfterEach
    void tearDownConsumer() {
        if (testConsumer != null) {
            testConsumer.close();
        }
    }

    @Test
    @DisplayName("Событие ДОЛЖНО быть отправлено в Kafka после коммита транзакции со статусом SUCCESS")
    void shouldSendKafkaEventWhenOrderStatusUpdatedToSuccess() {
        UUID orderId = UUID.randomUUID();
        Long clientId = 555L;
        Integer amount = 1500;

        Order order = new Order();
        order.setId(orderId);
        order.setInternalId(UUID.randomUUID().toString());
        order.setClientId(clientId);
        order.setAmount(amount);
        order.setMerchant(Merchant.ALFA_TEAM);
        order.setMerchantOrderStatus("qwerty");
        order.setMerchantOrderId("12345");
        order.setStatus(OrderStatus.NEW);
        order.setCreatedAt(Instant.now());

        orderRepository.saveAndFlush(order);
        orderService.updateStatus(orderId.toString(), OrderStatus.SUCCESS);

        ConsumerRecord<String, OrderConfirmationEvent> receivedRecord =
                KafkaTestUtils.getSingleRecord(testConsumer, kafkaTopic, Duration.ofSeconds(5));

        assertNotNull(receivedRecord, "Сообщение не было доставлено в топик " + kafkaTopic);

        OrderConfirmationEvent eventPayload = receivedRecord.value();
        assertEquals(clientId, eventPayload.getClientId());
        assertEquals("Зачисление по подтвержденному ордеру " + orderId, eventPayload.getComment());
        assertEquals(0, amount.compareTo(eventPayload.getAmount()));
    }

    @Test
    @DisplayName("Событие НЕ должно отправляться в Kafka, если статус заказа изменен НЕ на SUCCESS")
    void shouldNotSendKafkaEventWhenOrderStatusUpdatedToTimeout() {
        UUID orderId = UUID.randomUUID();
        Integer amount = 1500;
        Order order = new Order();
        order.setId(orderId);
        order.setInternalId(UUID.randomUUID().toString());
        order.setClientId(777L);
        order.setAmount(amount);
        order.setMerchant(Merchant.ALFA_TEAM);
        order.setMerchantOrderStatus("qwerty");
        order.setMerchantOrderId("12345");
        order.setStatus(OrderStatus.NEW);
        order.setCreatedAt(Instant.now());

        orderRepository.saveAndFlush(order);

        orderService.updateStatus(orderId.toString(), OrderStatus.TIMEOUT);

        org.awaitility.Awaitility.await()
                .during(1, java.util.concurrent.TimeUnit.SECONDS)
                .atMost(2, java.util.concurrent.TimeUnit.SECONDS)
                .until(() -> {
                    ConsumerRecords<String, OrderConfirmationEvent> records = testConsumer.poll(
                            Duration.ofMillis(100));

                    return java.util.stream.StreamSupport.stream(records.spliterator(), false)
                            .noneMatch(kafkaRecord ->
                                    kafkaRecord.value() != null && kafkaRecord.value().getComment()
                                            .contains(orderId.toString()));
                });
    }

}