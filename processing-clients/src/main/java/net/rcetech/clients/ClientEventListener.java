package net.rcetech.clients;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.clients.service.ClientFacade;
import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.meta.billing.TransactionCreatedEvent;
import net.rcetech.meta.incidents.IncidentType;
import net.rcetech.meta.incidents.dto.BalanceUpdateErrorData;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class ClientEventListener {

    private final ClientFacade clientFacade;

    private final IncidentService incidentService;

    public ClientEventListener(ClientFacade clientFacade, IncidentService incidentService) {
        this.clientFacade = clientFacade;
        this.incidentService = incidentService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void transactionCreated(TransactionCreatedEvent event) {
        try {
            clientFacade.updateBalanceAfterTransaction(event.transactionId());
        }  catch (Exception e) {
            long timestamp = System.currentTimeMillis();
            log.error("{} Ошибка обновления баланса пользователя после создания транзакции {}: {}",
                    timestamp, event.transactionId(), e.getMessage(), e);
            incidentService.save(
                    IncidentType.BALANCE_UPDATE_ERROR,
                    new BalanceUpdateErrorData(event.transactionId(), event.clientId()),
                    timestamp
            );
        }
    }
}
