package net.rcetech.domain.service.billing;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.model.billing.Transaction;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.billing.TransactionRepository;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.billing.TransactionCreatedEvent;
import net.rcetech.meta.billing.TransactionType;
import net.rcetech.meta.billing.dto.ManualCorrectTransaction;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.exception.BaseException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.PredicateSpecification;
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

    private final ClientService clientService;

    public TransactionService(TransactionRepository transactionRepository, OrderService orderService,
                              ApplicationEventPublisher eventPublisher, ClientService clientService) {
        this.transactionRepository = transactionRepository;
        this.orderService = orderService;
        this.eventPublisher = eventPublisher;
        this.clientService = clientService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createAfterOrderConfirmation(UUID orderId) {
        Order order = orderService.findById(orderId)
                .orElseThrow(() -> new BaseException("Order " + orderId.toString() + " not found"));
        Transaction transaction = new Transaction();
        transaction.setCreatedAt(Instant.now());
        transaction.setClient(order.getClient());
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

    public <T> Page<T> findAll(PredicateSpecification<Transaction> filter, Pageable pageable, Class<T> projectionType) {
        return transactionRepository.findBy(filter,
                query -> query.as(projectionType)
                        .page(PageRequest.of(
                                pageable.getPageNumber(),
                                pageable.getPageSize(),
                                Sort.by(Sort.Order.desc("createdAt"))
                        )));
    }

    @Transactional
    public void createManualCorrect(UUID creatorId, ManualCorrectTransaction manualCorrectTransaction) {
        Optional<Client> maybeClient = clientService.findById(manualCorrectTransaction.clientId());
        if (maybeClient.isEmpty()) {
            throw new BadRequestException("Client " + manualCorrectTransaction.clientId() + " not found");
        }
        Transaction transaction = new Transaction();
        transaction.setCreatedAt(Instant.now());
        transaction.setClient(maybeClient.get());
        transaction.setOperation(manualCorrectTransaction.operation());
        transaction.setAmount(manualCorrectTransaction.amount());
        transaction.setType(TransactionType.MANUAL_CORRECT);
        transaction.setComment("Создано пользователем " + creatorId + ". Комментарий: " + manualCorrectTransaction.comment());
        transactionRepository.save(transaction);
    }
}
