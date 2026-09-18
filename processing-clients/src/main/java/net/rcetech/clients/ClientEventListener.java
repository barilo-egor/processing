package net.rcetech.clients;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.clients.service.ClientBalanceService;
import net.rcetech.meta.billing.TransactionCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class ClientEventListener {

    private final ClientBalanceService clientBalanceService;

    public ClientEventListener(ClientBalanceService clientBalanceService) {
        this.clientBalanceService = clientBalanceService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void transactionCreated(TransactionCreatedEvent event) {
        try {
            clientBalanceService.updateAfterTransaction(event.getTransactionId());
        }  catch (Exception e) {
            log.error("Ошибка обновления баланса пользователя после создания транзакции {}: {}",
                    event.getTransactionId(), e.getMessage(), e);
            // TODO Создание инцидента
        }
    }
}
