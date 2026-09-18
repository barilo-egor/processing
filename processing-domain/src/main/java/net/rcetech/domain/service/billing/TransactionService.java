package net.rcetech.domain.service.billing;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.model.billing.Transaction;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.billing.TransactionRepository;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.billing.TransactionCreatedEvent;
import net.rcetech.meta.billing.TransactionType;
import net.rcetech.meta.exception.BaseException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;

    private final OrderService orderService;

    private final ApplicationEventPublisher eventPublisher;

    public TransactionService(TransactionRepository transactionRepository, OrderService orderService,
                              ApplicationEventPublisher eventPublisher) {
        this.transactionRepository = transactionRepository;
        this.orderService = orderService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createAfterOrderConfirmation(UUID orderId) {
        Order order = orderService.findById(orderId)
                .orElseThrow(() -> new BaseException("Order " + orderId.toString() + " not found"));
        Transaction transaction = new Transaction();
        transaction.setClient(order.getClient());
        transaction.setCreatedAt(Instant.now());
        transaction.setOperation(Operation.CREDIT);
        transaction.setAmount(order.getAmount());
        transaction.setType(TransactionType.ORDER_CONFIRMATION);
        transaction.setComment("Подтверждение по ордеру " + order.getId() + ". Транзакция создана системой.");
        transactionRepository.save(transaction);
        eventPublisher.publishEvent(new TransactionCreatedEvent(
                this,
                transaction.getId()
        ));
    }

    public Optional<Transaction> findById(Long transactionId) {
        return transactionRepository.findById(transactionId);
    }
}
