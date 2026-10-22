package net.rcetech.billing;

import net.rcetech.billing.service.ClientBalanceService;
import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.model.billing.Transaction;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.billing.TransactionRepository;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.service.billing.TransactionService;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.domain.test.SharedMySQLContainer;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.billing.TransactionCreatedEvent;
import net.rcetech.meta.billing.TransactionType;
import net.rcetech.meta.config.MetaWebConfig;
import net.rcetech.meta.incidents.BalanceUpdateErrorEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.transaction.TestTransaction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        OrderService.class, ClientService.class, ClientBalanceService.class, TransactionListener.class,
        TransactionService.class, MetaWebConfig.class
})
@RecordApplicationEvents
class TransactionListenerTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SharedMySQLContainer.register(registry);
    }

    @MockitoBean
    private ClientMapper clientMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    Client getDummyClient() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test" + client.getId());
        client.setRegisteredAt(Instant.now());
        return clientRepository.save(client);
    }

    Transaction saveTransaction(Client client, TransactionType type, Operation operation, Integer amount) {
        Transaction transaction = new Transaction();
        transaction.setClient(client);
        transaction.setCreatedAt(Instant.now());
        transaction.setType(type);
        transaction.setOperation(operation);
        transaction.setAmount(amount);
        transaction.setComment("Транзакция создана тестом.");
        return transactionRepository.save(transaction);
    }

    void publishAndCommit(Transaction transaction, int times) {
        for (int i = 0; i < times; i++) {
            eventPublisher.publishEvent(new TransactionCreatedEvent(transaction.getId(), transaction.getClient().getId()));
        }
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
    }

    Integer actualBalance(Client client) {
        assertNotNull(client.getId());
        return clientRepository.findById(client.getId()).orElseThrow().getBalance();
    }

    @ParameterizedTest
    @CsvSource("""
            ORDER_CONFIRMATION,500
            MANUAL_CORRECT,489990
            MANUAL_CORRECT,1
            """)
    @DisplayName("После создания транзакции на пополнение баланс должен быть пересчитан с её учётом.")
    void transactionCreated_shouldRecalculateBalanceAfterCredit(TransactionType transactionType, Integer amount) {
        Client client = getDummyClient();
        Transaction transaction = saveTransaction(client, transactionType, Operation.CREDIT, amount);

        publishAndCommit(transaction, 1);

        assertEquals(amount, actualBalance(client));
    }

    @ParameterizedTest
    @CsvSource("""
            CLIENT_WITHDRAWAL,250
            MANUAL_CORRECT,4006
            MANUAL_CORRECT,2066
            """)
    @DisplayName("После создания транзакции на списание баланс должен быть пересчитан с её учётом.")
    void transactionCreated_shouldRecalculateBalanceAfterDebit(TransactionType transactionType, Integer amount) {
        Client client = getDummyClient();
        saveTransaction(client, TransactionType.MANUAL_CORRECT, Operation.CREDIT, amount + 1000);
        Transaction transaction = saveTransaction(client, transactionType, Operation.DEBIT, amount);

        publishAndCommit(transaction, 1);

        assertEquals(1000, actualBalance(client));
    }

    @ParameterizedTest
    @CsvSource("""
            700,93000
            15,0
            """)
    @DisplayName("После создания транзакции баланс должен стать равным сумме транзакций, даже если до этого был неверным.")
    void transactionCreated_shouldFixWrongBalance(Integer amount, Integer wrongBalance) {
        Client client = getDummyClient();
        client.setBalance(wrongBalance);
        saveTransaction(client, TransactionType.MANUAL_CORRECT, Operation.CREDIT, 5000);
        Transaction transaction = saveTransaction(client, TransactionType.CLIENT_WITHDRAWAL, Operation.DEBIT, amount);

        publishAndCommit(transaction, 1);

        assertEquals(5000 - amount, actualBalance(client));
    }

    @ParameterizedTest
    @CsvSource("""
            320,2
            8150,3
            """)
    @DisplayName("Повторная обработка события о транзакции не должна менять баланс.")
    void transactionCreated_shouldBeIdempotent(Integer amount, int times) {
        Client client = getDummyClient();
        Transaction transaction = saveTransaction(client, TransactionType.ORDER_CONFIRMATION, Operation.CREDIT, amount);

        publishAndCommit(transaction, times);

        assertEquals(amount, actualBalance(client));
    }

    @ParameterizedTest
    @CsvSource({
            "5e0a7b1c-3f2d-4c8e-9a6b-1d4f7e2c9b03,c2cbb845-7313-4106-a1a3-e9e4b328bbd9",
            "a91d4e6f-0b7c-4d2a-8e3f-6c5b9a1d7e48,fe4a4710-9c2e-4f04-b274-06802fbf2012"
    })
    @DisplayName("Должен быть создан инцидент, если произошла ошибка при обновлении баланса.")
    void transactionCreated_shouldCreateIncidentIfCantUpdateBalance(UUID transactionId, UUID clientId) {
        TransactionCreatedEvent event = new TransactionCreatedEvent(transactionId, clientId);
        long before = System.currentTimeMillis();
        eventPublisher.publishEvent(event);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        long after = System.currentTimeMillis();
        List<BalanceUpdateErrorEvent> events = applicationEvents.stream(BalanceUpdateErrorEvent.class).toList();
        assertEquals(1, events.size());
        BalanceUpdateErrorEvent incidentEvent = events.getFirst();
        assertAll(
                () -> assertEquals(transactionId, incidentEvent.transactionId()),
                () -> assertEquals(clientId, incidentEvent.clientId()),
                () -> assertTrue(incidentEvent.errorTimestamp() >= before && incidentEvent.errorTimestamp() <= after)
        );
    }

}