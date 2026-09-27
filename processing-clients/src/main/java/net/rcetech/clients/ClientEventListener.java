package net.rcetech.clients;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.clients.service.ClientFacade;
import net.rcetech.meta.billing.TransactionCreatedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class ClientEventListener {

    private final ClientFacade clientFacade;

    public ClientEventListener(ClientFacade clientFacade) {
        this.clientFacade = clientFacade;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void transactionCreated(TransactionCreatedEvent event) {
        try {
            clientFacade.updateBalanceAfterTransaction(event.getTransactionId());
        }  catch (Exception e) {
            log.error("Ошибка обновления баланса пользователя после создания транзакции {}: {}",
                    event.getTransactionId(), e.getMessage(), e);
            // TODO Создание инцидента
        }
    }
}
