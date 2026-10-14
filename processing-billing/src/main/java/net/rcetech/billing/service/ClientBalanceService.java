package net.rcetech.billing.service;

import jakarta.persistence.OptimisticLockException;
import net.rcetech.domain.service.billing.TransactionService;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.exception.BaseException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ClientBalanceService {

    record TransactionProjection(Long id, UUID clientId, Operation operation, Integer amount) {}

    private final TransactionService transactionService;

    private final ClientService clientService;

    public ClientBalanceService(TransactionService transactionService, ClientService clientService) {
        this.transactionService = transactionService;
        this.clientService = clientService;
    }

    @Retryable(value = OptimisticLockException.class, delay = 500, jitter = 100)
    public void recalculate(UUID clientId) {
        clientService.updateBalance(clientId, transactionService.calculateBalance(clientId));
    }

    @Retryable(value = OptimisticLockException.class, delay = 500, jitter = 100)
    public void updateBalanceAfterTransaction(Long transactionId) {
        TransactionProjection transaction = transactionService.findById(transactionId, TransactionProjection.class)
                .orElseThrow(() -> new BaseException("Transaction with id " + transactionId + " not found."));
        if (Operation.CREDIT.equals(transaction.operation())) {
            clientService.creditBalance(transaction.clientId(), transaction.amount());
        } else {
            clientService.debitBalance(transaction.clientId(), transaction.amount());
        }
    }
}
