package net.rcetech.billing;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.service.billing.TransactionService;
import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.meta.incidents.IncidentType;
import net.rcetech.meta.incidents.dto.TransactionCreationErrorData;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class TransactionEventListener {

    private final TransactionService transactionService;

    private final IncidentService incidentService;

    public TransactionEventListener(TransactionService transactionService, IncidentService incidentService) {
        this.transactionService = transactionService;
        this.incidentService = incidentService;
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
            incidentService.save(
                    IncidentType.TRANSACTION_CREATION_ERROR,
                    new TransactionCreationErrorData(event.orderId(), event.orderStatus()),
                    timestamp
            );
        }
    }
}
