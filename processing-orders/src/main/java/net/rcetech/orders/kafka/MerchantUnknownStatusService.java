package net.rcetech.orders.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile({ "!kafka-disabled" })
public class MerchantUnknownStatusService {

    private final KafkaTemplate<String, MerchantCallbackEvent> kafkaTemplateUnknownStatus;

    private final String receiveTopicName;

    public MerchantUnknownStatusService(KafkaTemplate<String, MerchantCallbackEvent> kafkaTemplateUnknownStatus,
            @Value("${kafka.topic.unknown-status.receive}") String receiveTopicName) {
        this.kafkaTemplateUnknownStatus = kafkaTemplateUnknownStatus;
        this.receiveTopicName = receiveTopicName;
    }

    /**
     * Отправляет событие с неизвестным статусом в выделенный топик Kafka.
     *
     * @param merchantCallbackEvent объект события, полученный от мерчанта
     */
    public void sendUnknownStatusCallback(MerchantCallbackEvent merchantCallbackEvent) {
        kafkaTemplateUnknownStatus.send(receiveTopicName, merchantCallbackEvent);
    }

}
