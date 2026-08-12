package net.rcetech.clients.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import net.rcetech.meta.clients.dto.WithdrawalRequestDTO;

@Component
@Slf4j
@Profile({ "!kafka-disabled" })
public class WithdrawalEventListener {

    private final KafkaTemplate<String, WithdrawalRequestDTO> withdrawalRequestKafkaTemplate;

    private final String receiveTopicName;

    public WithdrawalEventListener(KafkaTemplate<String, WithdrawalRequestDTO> withdrawalRequestKafkaTemplate,
            @Value("${kafka.topic.api-clients.receive}") String receiveTopicName) {
        this.withdrawalRequestKafkaTemplate = withdrawalRequestKafkaTemplate;
        this.receiveTopicName = receiveTopicName;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCreatedEvent(WithdrawalRequestDTO event) {
        withdrawalRequestKafkaTemplate.send(receiveTopicName, event);
    }

}
