package net.rcetech.orders.kafka;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.meta.orders.MerchantCallbackEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import net.rcetech.meta.orders.dto.OrderDTO;
import net.rcetech.domain.service.orders.OrderService;

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@Profile("!kafka-disabled")
public class MerchantCallbackConsumer {

    private final OrderService orderService;

    public MerchantCallbackConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Обрабатывает входящие callback-события от мерчантов из Kafka.
     * Валидирует поля события, обновляет статус заказа или перенаправляет
     * событие в очередь неизвестных статусов.
     *
     * @param consumerRecord запись из Kafka, содержащая событие {@link MerchantCallbackEvent}
     */
    @KafkaListener(topics = "${kafka.topic.merchant-details.callback}", groupId = "${kafka.group-id}",
            containerFactory = "merchantCallbackKafkaListenerContainerFactory")
    public void callback(ConsumerRecord<String, MerchantCallbackEvent> consumerRecord) {
        log.trace("Получен callback мерчанта. Key={}, value={}", consumerRecord.key(), consumerRecord.value());
        MerchantCallbackEvent event = consumerRecord.value();
        if (Objects.isNull(event.getMerchantOrderId())
                || Objects.isNull(event.getMerchant())
                || Objects.isNull(event.getStatus())
                || Objects.isNull(event.getStatusDescription())) {
            log.error("В callback мерчанта(key={}) отсутствует значение одного из поля: {}", consumerRecord.key(),
                    consumerRecord.value());
            return;
        }
        try {

            Optional<OrderDTO> optionalOrderDTO = orderService.findByMerchantOrderId(event.getMerchantOrderId());
            optionalOrderDTO.ifPresent(orderDTO -> orderService.updateStatusByMerchantStatus(orderDTO.getId(), event));
        } catch (Exception e) {
            log.error("Ошибка при попытке обновления статуса order по orderId={}.Event={}, message={}.",
                    event.getMerchantOrderId(), event, e.getMessage(), e);
        }
    }

}
