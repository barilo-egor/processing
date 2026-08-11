package net.rcetech.meta;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaLogErrorHandler extends DefaultErrorHandler {
    public void handle(ConsumerRecord<?, ?> consumerRecord, Exception e) {
        log.error("Ошибка обработки записи: key={}, value={}", consumerRecord.key(), consumerRecord.value(), e);
    }
}
