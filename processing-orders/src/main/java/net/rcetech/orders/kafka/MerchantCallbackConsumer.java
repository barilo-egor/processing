package net.rcetech.orders.kafka;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.domain.service.orders.MerchantCallbackService;
import net.rcetech.meta.incidents.IncidentType;
import net.rcetech.meta.incidents.dto.MerchantCallbackHandleErrorData;
import net.rcetech.meta.orders.MerchantCallbackEvent;
import net.rcetech.orders.callback.OrderCallbackResolver;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("!kafka-disabled")
public class MerchantCallbackConsumer {

    private final OrderCallbackResolver orderCallbackResolver;

    private final MerchantCallbackService merchantCallbackService;

    private final IncidentService incidentService;

    public MerchantCallbackConsumer(OrderCallbackResolver orderCallbackResolver,
                                    MerchantCallbackService merchantCallbackService, IncidentService incidentService) {
        this.orderCallbackResolver = orderCallbackResolver;
        this.merchantCallbackService = merchantCallbackService;
        this.incidentService = incidentService;
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
        try {
            merchantCallbackService.save(consumerRecord.value());
            orderCallbackResolver.resolve(consumerRecord.value());
        } catch (Exception e) {
            long timestamp = System.currentTimeMillis();
            log.error("{} Ошибка обработки КБ мерчанта: {}", timestamp, e.getMessage(), e);
            MerchantCallbackEvent event = consumerRecord.value();
            incidentService.save(
                    IncidentType.MERCHANT_CALLBACK_HANDLE_ERROR,
                    new MerchantCallbackHandleErrorData(
                            event.getMerchant(), event.getStatus(), event.getStatusDescription(), event.getMerchantOrderId()
                    ),
                    timestamp
            );
        }
    }

}
