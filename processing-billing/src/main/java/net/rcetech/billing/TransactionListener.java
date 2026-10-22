package net.rcetech.billing;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.billing.service.ClientBalanceService;
import net.rcetech.meta.billing.TransactionCreatedEvent;
import net.rcetech.meta.incidents.BalanceUpdateErrorEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class TransactionListener {

    private final ClientBalanceService clientBalanceService;

    private final ApplicationEventPublisher eventPublisher;

    public TransactionListener(ClientBalanceService clientBalanceService, ApplicationEventPublisher eventPublisher) {
        this.clientBalanceService = clientBalanceService;
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void transactionCreated(TransactionCreatedEvent event) {
        try {
            clientBalanceService.recalculate(event.clientId());
        }  catch (Exception e) {
            long timestamp = System.currentTimeMillis();
            log.error("{} Ошибка обновления баланса пользователя после создания транзакции {}: {}",
                    timestamp, event.transactionId(), e.getMessage(), e);
            eventPublisher.publishEvent(new BalanceUpdateErrorEvent(event.transactionId(), event.clientId(), timestamp));
        }
    }
}
