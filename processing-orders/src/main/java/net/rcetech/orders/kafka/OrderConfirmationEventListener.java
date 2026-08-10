package net.rcetech.orders.kafka;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import net.rcetech.orders.dto.OrderDTO;
import net.rcetech.orders.enums.Operation;
import net.rcetech.orders.enums.OrderStatus;
import net.rcetech.orders.enums.TransactionType;

@Component
@Slf4j
@Profile({ "!kafka-disabled" })
public class OrderConfirmationEventListener {

    private final KafkaTemplate<String, OrderConfirmationEvent> kafkaTemplate;

    private final String receiveTopicName;

    private final TimeBasedEpochGenerator generator = Generators.timeBasedEpochGenerator();

    public OrderConfirmationEventListener(KafkaTemplate<String, OrderConfirmationEvent> kafkaTemplate,
            @Value("${kafka.topic.orders.receive}") String receiveTopicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.receiveTopicName = receiveTopicName;
    }

    /**
     * Пост-транзакционный обработчик для отправки уведомления в Kafka при успешном завершении заказа.
     * <p>
     * Метод срабатывает асинхронно после коммита транзакции БД ({@link TransactionPhase#AFTER_COMMIT}).
     * Если статус заказа {@link OrderDTO#getStatus()} равен {@link OrderStatus#SUCCESS},
     * формируется и отправляется событие подтверждения в топик Kafka.
     *
     * @param orderDTO данные заказа для проверки статуса и отправки уведомления
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCreatedEvent(OrderDTO orderDTO) {
        if (OrderStatus.SUCCESS.equals(orderDTO.getStatus())) {
            log.info("Транзакция успешно закоммичена. Пост-логика отправки kafka для заказа {}", orderDTO.getId());
            kafkaTemplate.send(receiveTopicName, new OrderConfirmationEvent(
                    generator.generate(),
                    orderDTO.getClientId(),
                    orderDTO.getAmount(),
                    Operation.CREDIT,
                    TransactionType.ORDER_CONFIRMATION,
                    orderDTO.getId()));
        }

    }

}
