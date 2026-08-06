package tgb.cryptoexchange.clientsapi.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tgb.cryptoexchange.clientsapi.dto.WithdrawalRequestDTO;

@Component
@Slf4j
@Profile({ "!kafka-disabled" })
public class WithdrawalEventListener {

    private final KafkaTemplate<String, WithdrawalRequestDTO> kafkaTemplate;

    private final String receiveTopicName;

    public WithdrawalEventListener(KafkaTemplate<String, WithdrawalRequestDTO> kafkaTemplate,
            @Value("${kafka.topic.api-clients.receive}") String receiveTopicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.receiveTopicName = receiveTopicName;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCreatedEvent(WithdrawalRequestDTO event) {
        kafkaTemplate.send(receiveTopicName, event);
    }

}
