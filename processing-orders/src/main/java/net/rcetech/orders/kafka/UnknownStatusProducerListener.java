package net.rcetech.orders.kafka;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile({ "!kafka-disabled" })
public class UnknownStatusProducerListener implements ProducerListener<String, MerchantCallbackEvent> {

    @Override
    public void onSuccess(
            ProducerRecord<String, MerchantCallbackEvent> producerRecord, @Nullable RecordMetadata recordMetadata) {
        log.debug("Успешно отправлен ивент. Key={}, event={}.", producerRecord.key(), producerRecord.value());
    }

    @Override
    public void onError(ProducerRecord<String, MerchantCallbackEvent> producerRecord, RecordMetadata recordMetadata,
            @Nullable Exception exception) {
        log.error("Ошибка при попытке отправить ивент в топик. Key={}, event={}.",
                producerRecord.key(), producerRecord.value(), exception);
    }

}
