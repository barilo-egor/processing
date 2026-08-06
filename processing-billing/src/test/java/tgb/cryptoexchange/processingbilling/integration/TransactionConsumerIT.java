package tgb.cryptoexchange.processingbilling.integration;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import tgb.cryptoexchange.processingbilling.dto.TransactionDTO;
import tgb.cryptoexchange.processingbilling.enums.Operation;
import tgb.cryptoexchange.processingbilling.enums.TransactionType;

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

class TransactionConsumerIT extends BaseIntegrationTest {

    private final TimeBasedEpochGenerator generator = Generators.timeBasedEpochGenerator();

    @Value("${kafka.topic.billing.transaction}")
    private String inputTopic;

    @Autowired
    private KafkaTemplate<String, TransactionDTO> rawKafkaTemplate;

    @Test
    @DisplayName("Сохранение transaction из kafka")
    void shouldUpdateOrderStatusToSuccessWhenStatusIsSuccessful() throws Exception {
        UUID transactionId = generator.generate();
        TransactionDTO event = TransactionDTO.builder()
                .id(transactionId)
                .clientId(42L)
                .amount(1000)
                .operation(Operation.CREDIT)
                .type(TransactionType.CLIENT_WITHDRAWAL)
                .build();

        rawKafkaTemplate.send(inputTopic, transactionId.toString(), event).get();

        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> transactionRepository.findById(transactionId)
                        .orElseThrow(() -> new AssertionError("Transaction не попала в БД")));
    }

}
