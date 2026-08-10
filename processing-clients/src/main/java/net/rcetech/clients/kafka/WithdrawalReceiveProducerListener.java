package net.rcetech.clients.kafka;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.stereotype.Service;
import net.rcetech.clients.dto.WithdrawalRequestDTO;

@Slf4j
@Service
@Profile({ "!kafka-disabled" })
public class WithdrawalReceiveProducerListener implements ProducerListener<String, WithdrawalRequestDTO> {

    @Override
    public void onSuccess(ProducerRecord<String, WithdrawalRequestDTO> producerRecord,
            @Nullable RecordMetadata recordMetadata) {
        log.debug("Успешно отправлен ивент. Key={}, event={}.", producerRecord.key(), producerRecord.value());
    }

    @Override
    public void onError(ProducerRecord<String, WithdrawalRequestDTO> producerRecord, RecordMetadata recordMetadata,
            @Nullable Exception exception) {
        log.error("Ошибка при попытке отправить ивент в топик. Key={}, event={}.",
                producerRecord.key(), producerRecord.value(), exception);
    }

}
