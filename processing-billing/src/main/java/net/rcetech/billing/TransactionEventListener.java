package net.rcetech.billing;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.service.billing.TransactionService;
import net.rcetech.meta.incidents.TransactionCreationErrorEvent;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class TransactionEventListener {

    private final TransactionService transactionService;

    private final ApplicationEventPublisher eventPublisher;

    public TransactionEventListener(TransactionService transactionService, ApplicationEventPublisher eventPublisher) {
        this.transactionService = transactionService;
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void orderStatusUpdated(OrderStatusUpdatedEvent event) {
        try {
            if (OrderStatus.SUCCESS.equals(event.orderStatus())) {
                transactionService.createAfterOrderConfirmation(event.orderId());
            }
        } catch (Exception e) {
            long timestamp = System.currentTimeMillis();
            log.error("{} Ошибка при попытке создать транзакцию после обновления статуса ордера {}: {}",
                    timestamp, event.orderId(), e.getMessage(), e);
            eventPublisher.publishEvent(
                    new TransactionCreationErrorEvent(event.orderId(), event.orderStatus(), timestamp));
        }
    }
}
