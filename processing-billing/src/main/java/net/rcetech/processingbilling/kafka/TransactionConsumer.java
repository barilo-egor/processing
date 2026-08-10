package net.rcetech.processingbilling.kafka;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import net.rcetech.processingbilling.dto.TransactionDTO;
import net.rcetech.processingbilling.service.TransactionService;

@Slf4j
@Service
@Profile("!kafka-disabled")
public class TransactionConsumer {

    private final TransactionService transactionService;

    public TransactionConsumer(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Слушатель топика Kafka для обработки и сохранения transaction.
     * <p>
     * Выполняет автоматическую валидацию входящего DTO. В случае сбоя бизнес-логики
     * или ошибки базы данных перехватывает исключение для предотвращения блокировки топика.
     *
     * @param dto валидированные данные transaction из тела сообщения
     * @param key опциональный ключ сообщения Kafka
     */
    @KafkaListener(topics = "${kafka.topic.billing.transaction}", groupId = "${kafka.group-id}",
            containerFactory = "transactionKafkaListenerContainerFactory")
    public void saveTransaction(@Valid @Payload TransactionDTO dto,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        log.trace("Получена transaction. Key={}, value={}", key, dto);
        try {
            transactionService.save(dto);
        } catch (Exception e) {
            log.error("Ошибка при попытке сохранения transaction {}", dto.getId());
        }
    }

}
