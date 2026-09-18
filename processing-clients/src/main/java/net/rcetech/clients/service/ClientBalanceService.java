package net.rcetech.clients.service;

import net.rcetech.domain.model.billing.Transaction;
import net.rcetech.domain.service.billing.TransactionService;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.exception.BaseException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientBalanceService {

    private final TransactionService transactionService;

    private final ClientService clientService;

    public ClientBalanceService(TransactionService transactionService, ClientService clientService) {
        this.transactionService = transactionService;
        this.clientService = clientService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Retryable(maxRetries = 3, delay = 100, value =  ObjectOptimisticLockingFailureException.class)
    public void updateAfterTransaction(Long transactionId) {
        Transaction transaction = transactionService.findById(transactionId)
                .orElseThrow(() -> new BaseException("Transaction with id " + transactionId + " not found."));
        if (Operation.CREDIT.equals(transaction.getOperation())) {
            clientService.creditBalance(transaction.getClient().getId(), transaction.getAmount());
        } else {
            clientService.debitBalance(transaction.getClient().getId(), transaction.getAmount());
        }
    }
}
