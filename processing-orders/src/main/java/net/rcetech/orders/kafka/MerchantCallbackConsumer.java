package net.rcetech.orders.kafka;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.meta.orders.MerchantCallbackEvent;
import net.rcetech.orders.callback.OrderCallbackService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("!kafka-disabled")
public class MerchantCallbackConsumer {

    private final OrderCallbackService orderCallbackService;

    public MerchantCallbackConsumer(OrderCallbackService orderCallbackService) {
        this.orderCallbackService = orderCallbackService;
    }

    /**
     * Обрабатывает входящие callback-события от мерчантов из Kafka.
     * Валидирует поля события, обновляет статус заказа или перенаправляет
     * событие в очередь неизвестных статусов.
     *
     * @param consumerRecord запись из Kafka, содержащая событие {@link MerchantCallbackEvent}
     */
    @KafkaListener(
            topics = "${kafka.topic.merchant-details.callback}",
            groupId = "${kafka.group-id}",
            containerFactory = "merchantCallbackKafkaListenerContainerFactory"
    )
    public void callback(ConsumerRecord<String, MerchantCallbackEvent> consumerRecord) {
        log.debug("Принят callback key={} : {}", consumerRecord.key(), consumerRecord.value());
        // TODO создать сущность MerchantCallback, сохранять каждый
        orderCallbackService.resolve(consumerRecord.value());
    }

}
