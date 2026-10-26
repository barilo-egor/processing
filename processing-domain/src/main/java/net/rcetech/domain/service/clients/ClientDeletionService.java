package net.rcetech.domain.service.clients;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.clients.IpAddress;
import net.rcetech.domain.repository.billing.TransactionRepository;
import net.rcetech.domain.repository.billing.WithdrawalRequestRepository;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.MerchantCallbackRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.meta.clients.ClientDeletedEvent;
import net.rcetech.meta.clients.IpAddressesChangedEvent;
import net.rcetech.meta.exception.BadRequestException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ClientDeletionService {

    private final ClientRepository clientRepository;

    private final OrderRepository orderRepository;

    private final MerchantCallbackRepository merchantCallbackRepository;

    private final WithdrawalRequestRepository withdrawalRequestRepository;

    private final TransactionRepository transactionRepository;

    private final ApplicationEventPublisher eventPublisher;

    public ClientDeletionService(ClientRepository clientRepository, OrderRepository orderRepository,
                                 MerchantCallbackRepository merchantCallbackRepository,
                                 WithdrawalRequestRepository withdrawalRequestRepository,
                                 TransactionRepository transactionRepository,
                                 ApplicationEventPublisher eventPublisher) {
        this.clientRepository = clientRepository;
        this.orderRepository = orderRepository;
        this.merchantCallbackRepository = merchantCallbackRepository;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.transactionRepository = transactionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void delete(UUID id) {
        Client client = clientRepository.findForUpdateById(id)
                .orElseThrow(() -> new BadRequestException("Клиент не найден."));
        List<String> ipAddresses = client.getIpAddresses().stream().map(IpAddress::getAddress).toList();
        int callbacks = merchantCallbackRepository.detachOrdersByClientId(id);
        int orders = orderRepository.deleteAllByClientId(id);
        int withdrawalRequests = withdrawalRequestRepository.deleteAllByClientId(id);
        int transactions = transactionRepository.deleteAllByClientId(id);
        clientRepository.delete(client);
        clientRepository.flush();
        eventPublisher.publishEvent(new ClientDeletedEvent(id));
        if (!ipAddresses.isEmpty()) {
            eventPublisher.publishEvent(new IpAddressesChangedEvent(ipAddresses));
        }
        log.info("Удалён клиент {} ({}): ордеров {}, заявок на вывод {}, транзакций {}, отвязано КБ {}.",
                id, client.getUsername(), orders, withdrawalRequests, transactions, callbacks);
    }
}
