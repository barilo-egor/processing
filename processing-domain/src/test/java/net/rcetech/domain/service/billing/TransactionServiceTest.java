package net.rcetech.domain.service.billing;

import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.model.billing.Transaction;
import net.rcetech.domain.model.billing.WithdrawalRequest;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.billing.TransactionRepository;
import net.rcetech.domain.repository.billing.TransactionSpecification;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.domain.test.SharedMySQLContainer;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.billing.TransactionCreatedEvent;
import net.rcetech.meta.billing.TransactionType;
import net.rcetech.meta.billing.dto.*;
import net.rcetech.meta.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OrderService.class, ClientService.class, TransactionService.class})
@RecordApplicationEvents
class TransactionServiceTest {

    @TestConfiguration
    static class Configuration {

        @Bean
        public ClientMapper clientMapper() {
            return Mappers.getMapper(ClientMapper.class);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SharedMySQLContainer.register(registry);
    }

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private TransactionService transactionService;

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

    void getDummyTransaction(Client client) {
        Transaction transaction = new Transaction();
        transaction.setClient(client);
        fillFields(transaction);
        transactionRepository.save(transaction);
    }

    void fillFields(Transaction transaction) {
        if (Objects.isNull(transaction.getCreatedAt())) {
            transaction.setCreatedAt(Instant.now());
        }
        if (Objects.isNull(transaction.getOperation())) {
            transaction.setOperation(Operation.CREDIT);
        }
        if (Objects.isNull(transaction.getAmount())) {
            transaction.setAmount(1000);
        }
        if (Objects.isNull(transaction.getType())) {
            transaction.setType(TransactionType.ORDER_CONFIRMATION);
        }
        if (Objects.isNull(transaction.getComment())) {
            transaction.setComment("Подтверждение по ордеру 123. Создано системой.");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "CREDIT,5396,Технический сбой.",
            "DEBIT,26222,двойное списание"
    })
    @DisplayName("Метод должен создать транзакцию ручной корректировки.")
    void createManualCorrect_shouldCreateTransaction(Operation operation, Integer amount, String comment) {
        UUID creatorId = UUID.randomUUID();
        Client client = getDummyClient();
        ManualCorrectTransaction manualCorrectTransaction = new ManualCorrectTransaction(
                client.getId(), operation, amount, comment
        );
        transactionService.createManualCorrect(creatorId, manualCorrectTransaction);
        List<Transaction> transactions = transactionRepository.findAll();
        assertEquals(1, transactions.size());
        Transaction transaction = transactions.getFirst();
        assertAll(
                () -> assertNotNull(transaction.getCreatedAt()),
                () -> assertEquals(client.getId(), transaction.getClient().getId()),
                () -> assertEquals(operation, transaction.getOperation()),
                () -> assertEquals(amount, transaction.getAmount()),
                () -> assertEquals(TransactionType.MANUAL_CORRECT, transaction.getType()),
                () -> assertEquals(
                        "Создано пользователем " + creatorId + ". Комментарий: " + manualCorrectTransaction.comment(),
                        transaction.getComment()
                )
        );
        Optional<TransactionCreatedEvent> maybeEvent = applicationEvents.stream(TransactionCreatedEvent.class).findFirst();
        assertTrue(maybeEvent.isPresent());
        assertAll(
                () -> assertEquals(transaction.getId(), maybeEvent.get().transactionId()),
                () -> assertEquals(transaction.getClient().getId(), maybeEvent.get().clientId())
        );
    }

    @ParameterizedTest
    @CsvSource({
            "0fae41bc-c171-4ad7-b4bc-1132a8149407,1000",
            "186f758e-53ab-4973-98af-a924d1b94a10,48250"
    })
    @DisplayName("Метод должен создать транзакцию списания по подтверждённой заявке на вывод.")
    void createAfterWithdrawalApproval_shouldCreateDebitTransaction(UUID requestId, Integer amount) {
        Client client = getDummyClient();
        WithdrawalRequest withdrawalRequest = new WithdrawalRequest();
        withdrawalRequest.setId(requestId);
        withdrawalRequest.setClient(client);
        withdrawalRequest.setGrossSourceAmount(amount);

        transactionService.createAfterWithdrawalApproval(withdrawalRequest);

        List<Transaction> transactions = transactionRepository.findAll();
        assertEquals(1, transactions.size());
        Transaction transaction = transactions.getFirst();
        assertAll(
                () -> assertNotNull(transaction.getCreatedAt()),
                () -> assertEquals(client.getId(), transaction.getClient().getId()),
                () -> assertEquals(Operation.DEBIT, transaction.getOperation()),
                () -> assertEquals(amount, transaction.getAmount()),
                () -> assertEquals(TransactionType.CLIENT_WITHDRAWAL, transaction.getType()),
                () -> assertEquals(
                        "Подтверждение заявки на вывод " + requestId + ". Транзакция создана системой.",
                        transaction.getComment()
                )
        );
        Optional<TransactionCreatedEvent> maybeEvent = applicationEvents.stream(TransactionCreatedEvent.class).findFirst();
        assertTrue(maybeEvent.isPresent());
        assertAll(
                () -> assertEquals(transaction.getId(), maybeEvent.get().transactionId()),
                () -> assertEquals(client.getId(), maybeEvent.get().clientId())
        );
    }

    @Test
    @DisplayName("Метод должен бросить BadRequestException, если клиент не найден.")
    void createManualCorrect_shouldThrowBadRequestExceptionIfClientNotFound() {
        UUID id = UUID.randomUUID();
        Client client = getDummyClient();
        assertNotEquals(id, client.getId());
        ManualCorrectTransaction manualCorrectTransaction = new ManualCorrectTransaction(id, null, null, null);
        UUID clientId = client.getId();
        assertThrows(BadRequestException.class, () -> transactionService.createManualCorrect(clientId, manualCorrectTransaction));
    }

    @Test
    @DisplayName("Метод должен вернуть все транзакции, если фильтр TransactionFilter равен null.")
    void findAllTransactionFilter_shouldReturnAllTransactionsIfNullFilter() {
        Client client = getDummyClient();
        for (int i = 0; i < 2; i++) {
            getDummyTransaction(client);
        }
        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(null),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );
        assertEquals(2, actual.getTotalElements());
    }

    @Test
    @DisplayName("Метод должен вернуть транзакции найденные по id клиента в фильтре TransactionFilter.")
    void findAllTransactionFilter_shouldReturnTransactionsByClientId() {
        Client client = getDummyClient();
        Client targetClient = getDummyClient();
        getDummyTransaction(client);
        getDummyTransaction(targetClient);
        assertNotNull(targetClient.getId());

        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(new TransactionFilter(null, targetClient.getId().toString(), null, null)),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(targetClient.getId(), actual.getContent().getFirst().clientId());
    }

    @Test
    @DisplayName("Метод должен вернуть транзакции найденные по username клиента в фильтре TransactionFilter.")
    void findAllTransactionFilter_shouldReturnTransactionsByClientUsername() {
        Client client = getDummyClient();
        Client targetClient = getDummyClient();
        getDummyTransaction(client);
        getDummyTransaction(targetClient);
        assertNotNull(targetClient.getId());

        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(new TransactionFilter(null, "test" + targetClient.getId(), null, null)),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(targetClient.getId(), actual.getContent().getFirst().clientId());
    }

    @ValueSource(longs = {
            1789912612000L, 1789912712302L
    })
    @ParameterizedTest
    @DisplayName("Метод должен вернуть транзакции найденные по createdAt ОТ в фильтре TransactionFilter.")
    void findAllTransactionFilter_shouldReturnTransactionsByCreatedAtFrom(long millis) {
        Client client = getDummyClient();
        Transaction transaction = new Transaction();
        transaction.setClient(client);
        transaction.setCreatedAt(Instant.ofEpochMilli(millis + 10000L));
        fillFields(transaction);
        transactionRepository.save(transaction);

        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(new TransactionFilter(null, null, Instant.ofEpochMilli(millis), null)),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
    }

    @ValueSource(longs = {
            1789912612000L, 1789912712302L
    })
    @ParameterizedTest
    @DisplayName("Метод должен вернуть транзакции найденные по createdAt ДО в фильтре TransactionFilter.")
    void findAllTransactionFilter_shouldReturnTransactionsByCreatedAtTo(long millis) {
        Client client = getDummyClient();
        Transaction transaction = new Transaction();
        transaction.setClient(client);
        transaction.setCreatedAt(Instant.ofEpochMilli(millis - 50000L));
        fillFields(transaction);
        transactionRepository.save(transaction);

        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(new TransactionFilter(null, null, null, Instant.ofEpochMilli(millis))),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
    }

    @Test
    @DisplayName("Метод должен вернуть транзакции клиента, если фильтр ClientTransactionFilter равен null.")
    void findAllClientTransactionFilter_shouldReturnClientTransactionsIfNullFilter() {
        Client client = getDummyClient();
        Client targetClient = getDummyClient();
        for (int i = 0; i < 2; i++) {
            getDummyTransaction(client);
            getDummyTransaction(targetClient);
        }
        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(targetClient.getId(), null),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );
        assertEquals(2, actual.getTotalElements());
        assertTrue(actual.get().allMatch(t -> t.clientId().equals(targetClient.getId())));
    }

    @ValueSource(longs = {
            1789912612000L, 1789912712302L
    })
    @ParameterizedTest
    @DisplayName("Метод должен вернуть транзакции, найденные по createdAt ОТ по фильтру ClientTransactionFilter.")
    void findAllClientTransactionFilter_shouldReturnTransactionsByCreatedAtFrom(long millis) {
        Client client = getDummyClient();
        Transaction transaction = new Transaction();
        transaction.setClient(client);
        transaction.setCreatedAt(Instant.ofEpochMilli(millis + 10000L));
        fillFields(transaction);
        transactionRepository.save(transaction);

        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(client.getId(), new ClientTransactionFilter(null, Instant.ofEpochMilli(millis), null)),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
    }

    @ValueSource(longs = {
            1789912612000L, 1789912712302L
    })
    @ParameterizedTest
    @DisplayName("Метод должен вернуть транзакции, найденные по createdAt ДО по фильтру ClientTransactionFilter.")
    void findAllClientTransactionFilter_shouldReturnTransactionsByCreatedAtTo(long millis) {
        Client client = getDummyClient();
        Transaction transaction = new Transaction();
        transaction.setClient(client);
        transaction.setCreatedAt(Instant.ofEpochMilli(millis - 50000L));
        fillFields(transaction);
        transactionRepository.save(transaction);

        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(client.getId(), new ClientTransactionFilter(null, null, Instant.ofEpochMilli(millis))),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
    }

    @ValueSource(longs = {
            1789912612000L, 1789912713000L
    })
    @ParameterizedTest
    @DisplayName("Метод должен включать границу createdAt ОТ в фильтре TransactionFilter.")
    void findAllTransactionFilter_shouldIncludeCreatedAtFromBoundary(long millis) {
        Instant from = Instant.ofEpochMilli(millis);
        Client client = getDummyClient();
        saveTransaction(client, from);
        saveTransaction(client, from.minusSeconds(1));

        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(new TransactionFilter(null, null, from, null)),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(from, actual.getContent().getFirst().createdAt());
    }

    @ValueSource(longs = {
            1789912612000L, 1789912713000L
    })
    @ParameterizedTest
    @DisplayName("Метод должен исключать границу createdAt ДО в фильтре TransactionFilter.")
    void findAllTransactionFilter_shouldExcludeCreatedAtToBoundary(long millis) {
        Instant to = Instant.ofEpochMilli(millis);
        Client client = getDummyClient();
        saveTransaction(client, to);
        saveTransaction(client, to.minusSeconds(1));

        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(new TransactionFilter(null, null, null, to)),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(to.minusSeconds(1), actual.getContent().getFirst().createdAt());
    }

    @ValueSource(longs = {
            1789912612000L, 1789912713000L
    })
    @ParameterizedTest
    @DisplayName("Метод должен включать границу createdAt ОТ в фильтре ClientTransactionFilter.")
    void findAllClientTransactionFilter_shouldIncludeCreatedAtFromBoundary(long millis) {
        Instant from = Instant.ofEpochMilli(millis);
        Client client = getDummyClient();
        saveTransaction(client, from);
        saveTransaction(client, from.minusSeconds(1));

        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(client.getId(), new ClientTransactionFilter(null, from, null)),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(from, actual.getContent().getFirst().createdAt());
    }

    @ValueSource(longs = {
            1789912612000L, 1789912713000L
    })
    @ParameterizedTest
    @DisplayName("Метод должен исключать границу createdAt ДО в фильтре ClientTransactionFilter.")
    void findAllClientTransactionFilter_shouldExcludeCreatedAtToBoundary(long millis) {
        Instant to = Instant.ofEpochMilli(millis);
        Client client = getDummyClient();
        saveTransaction(client, to);
        saveTransaction(client, to.minusSeconds(1));

        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(client.getId(), new ClientTransactionFilter(null, null, to)),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(to.minusSeconds(1), actual.getContent().getFirst().createdAt());
    }

    @ParameterizedTest
    @ValueSource(ints = {1500, 73042})
    @DisplayName("Метод должен вернуть идентификатор транзакции в проекции TransactionResponse.")
    void findAllTransactionFilter_shouldReturnTransactionId(Integer amount) {
        Transaction transaction = new Transaction();
        transaction.setClient(getDummyClient());
        transaction.setAmount(amount);
        fillFields(transaction);
        UUID transactionId = transactionRepository.save(transaction).getId();

        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(null),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
        assertNotNull(transactionId);
        assertEquals(transactionId, actual.getContent().getFirst().id());
    }

    @ParameterizedTest
    @ValueSource(ints = {1500, 73042})
    @DisplayName("Метод должен вернуть идентификатор транзакции в проекции ClientTransactionResponse.")
    void findAllClientTransactionFilter_shouldReturnTransactionId(Integer amount) {
        Client client = getDummyClient();
        Transaction transaction = new Transaction();
        transaction.setClient(client);
        transaction.setAmount(amount);
        fillFields(transaction);
        UUID transactionId = transactionRepository.save(transaction).getId();

        Page<ClientTransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(client.getId(), null),
                PageRequest.of(0, 20),
                ClientTransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
        assertNotNull(transactionId);
        assertEquals(transactionId, actual.getContent().getFirst().id());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 5})
    @DisplayName("Метод должен вернуть транзакцию, найденную по id в фильтре TransactionFilter.")
    void findAllTransactionFilter_shouldReturnTransactionById(int count) {
        Client client = getDummyClient();
        for (int i = 0; i < count; i++) {
            saveTransaction(client, Operation.CREDIT, 1000);
        }
        UUID targetId = saveTransaction(getDummyClient(), Operation.DEBIT, 500);

        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(new TransactionFilter(targetId, null, null, null)),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(targetId, actual.getContent().getFirst().id());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 5})
    @DisplayName("Метод должен вернуть транзакцию клиента, найденную по id в фильтре ClientTransactionFilter.")
    void findAllClientTransactionFilter_shouldReturnTransactionById(int count) {
        Client client = getDummyClient();
        for (int i = 0; i < count; i++) {
            saveTransaction(client, Operation.CREDIT, 1000);
        }
        UUID targetId = saveTransaction(client, Operation.DEBIT, 500);

        Page<ClientTransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(client.getId(), new ClientTransactionFilter(targetId, null, null)),
                PageRequest.of(0, 20),
                ClientTransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(targetId, actual.getContent().getFirst().id());
    }

    @ParameterizedTest
    @ValueSource(ints = {1000, 4520})
    @DisplayName("Метод не должен вернуть транзакцию другого клиента по id в фильтре ClientTransactionFilter.")
    void findAllClientTransactionFilter_shouldNotReturnOtherClientTransactionById(Integer amount) {
        Client client = getDummyClient();
        saveTransaction(client, Operation.CREDIT, amount);
        UUID otherClientTransactionId = saveTransaction(getDummyClient(), Operation.CREDIT, amount);

        Page<ClientTransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(client.getId(), new ClientTransactionFilter(otherClientTransactionId, null, null)),
                PageRequest.of(0, 20),
                ClientTransactionResponse.class
        );

        assertEquals(0, actual.getTotalElements());
    }

    void saveTransaction(Client client, Instant createdAt) {
        Transaction transaction = new Transaction();
        transaction.setClient(client);
        transaction.setCreatedAt(createdAt);
        fillFields(transaction);
        transactionRepository.save(transaction);
    }

    UUID saveTransaction(Client client, Operation operation, Integer amount) {
        Transaction transaction = new Transaction();
        transaction.setClient(client);
        transaction.setOperation(operation);
        transaction.setAmount(amount);
        fillFields(transaction);
        return transactionRepository.save(transaction).getId();
    }

    @ParameterizedTest
    @CsvSource({
            "1000,300,700",
            "250,4006,-3756"
    })
    @DisplayName("Метод должен посчитать баланс клиента: сумма пополнений минус сумма списаний.")
    void calculateBalance_shouldSumCreditsMinusDebits(Integer credit, Integer debit, Integer expected) {
        Client client = getDummyClient();
        saveTransaction(client, Operation.CREDIT, credit);
        saveTransaction(client, Operation.CREDIT, credit);
        saveTransaction(client, Operation.DEBIT, debit);
        saveTransaction(client, Operation.DEBIT, credit);
        saveTransaction(getDummyClient(), Operation.CREDIT, credit);

        assertEquals(expected, transactionService.calculateBalance(client.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"16af5227-d690-424f-9e36-89fd8b955146", "77fb78d1-e61f-4522-847e-4533f703f8dc"})
    @DisplayName("Метод должен вернуть 0, если у клиента нет транзакций.")
    void calculateBalance_shouldReturnZeroIfNoTransactions(UUID clientId) {
        saveTransaction(getDummyClient(), Operation.CREDIT, 1000);

        assertEquals(0, transactionService.calculateBalance(clientId));
    }
}
