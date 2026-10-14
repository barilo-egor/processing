package net.rcetech.billing.service;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.service.billing.TransactionService;
import net.rcetech.domain.service.clients.ClientService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ClientBalanceService {

    private final TransactionService transactionService;

    private final ClientService clientService;

    public ClientBalanceService(TransactionService transactionService, ClientService clientService) {
        this.transactionService = transactionService;
        this.clientService = clientService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recalculate(UUID clientId) {
        Client client = clientService.findForUpdate(clientId);
        client.setBalance(transactionService.calculateBalance(clientId));
    }
}
