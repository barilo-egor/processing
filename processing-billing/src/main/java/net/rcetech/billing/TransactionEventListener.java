package net.rcetech.billing;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.service.billing.TransactionService;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class TransactionEventListener {

    private final TransactionService transactionService;

    public TransactionEventListener(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void orderStatusUpdated(OrderStatusUpdatedEvent event) {
        try {
            if (OrderStatus.SUCCESS.equals(event.getOrderStatus())) {
                transactionService.createAfterOrderConfirmation(event.getOrderId());
            }
        } catch (Exception e) {
            log.error("Ошибка при попытке создать транзакцию после обновления статуса ордера {}: {}",
                    event.getOrderId(), e.getMessage(), e);
            // TODO Создание инцидента
        }
    }
}
