package net.rcetech.domain.service.billing;

import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.model.billing.Transaction;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.billing.TransactionRepository;
import net.rcetech.domain.repository.billing.TransactionSpecification;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.billing.TransactionType;
import net.rcetech.meta.billing.dto.ClientTransactionFilter;
import net.rcetech.meta.billing.dto.ManualCorrectTransaction;
import net.rcetech.meta.billing.dto.TransactionFilter;
import net.rcetech.meta.billing.dto.TransactionResponse;
import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.meta.exception.BadRequestException;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OrderService.class, ClientService.class, TransactionService.class})
class TransactionServiceTest {

    @TestConfiguration
    static class Configuration {

        @Bean
        public ClientMapper clientMapper() {
            return Mappers.getMapper(ClientMapper.class);
        }
    }

    @Container
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.0.46");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
    }

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    Client getDummyClient() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test" + client.getId());
        client.setRegisteredAt(Instant.now());
        client.setStatus(ClientStatus.ACTIVE);
        return clientRepository.save(client);
    }

    Transaction getDummyTransaction(Client client) {
        Transaction transaction = new Transaction();
        transaction.setClient(client);
        fillFields(transaction);
        return transactionRepository.save(transaction);
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
    }

    @Test
    void createManualCorrect_shouldThrowBadRequestExceptionIfClientNotFound() {
        UUID id = UUID.randomUUID();
        Client client = getDummyClient();
        assertNotEquals(id, client.getId());
        ManualCorrectTransaction manualCorrectTransaction = new ManualCorrectTransaction(id, null, null, null);
        UUID clientId = client.getId();
        assertThrows(BadRequestException.class, () -> transactionService.createManualCorrect(clientId, manualCorrectTransaction));
    }

    @Test
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
    void findAllTransactionFilter_shouldReturnTransactionsByClientId() {
        Client client = getDummyClient();
        Client targetClient = getDummyClient();
        getDummyTransaction(client);
        getDummyTransaction(targetClient);
        assertNotNull(targetClient.getId());

        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(new TransactionFilter(targetClient.getId().toString(), null, null)),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(targetClient.getId(), actual.getContent().getFirst().clientId());
    }

    @Test
    void findAllTransactionFilter_shouldReturnTransactionsByClientUsername() {
        Client client = getDummyClient();
        Client targetClient = getDummyClient();
        getDummyTransaction(client);
        getDummyTransaction(targetClient);
        assertNotNull(targetClient.getId());

        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(new TransactionFilter("test" + targetClient.getId(), null, null)),
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
    void findAllTransactionFilter_shouldReturnTransactionsByCreatedAtFrom(long millis) {
        Client client = getDummyClient();
        Transaction transaction = new Transaction();
        transaction.setClient(client);
        transaction.setCreatedAt(Instant.ofEpochMilli(millis + 10000L));
        fillFields(transaction);
        transactionRepository.save(transaction);

        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(new TransactionFilter(null, Instant.ofEpochMilli(millis), null)),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
    }

    @ValueSource(longs = {
            1789912612000L, 1789912712302L
    })
    @ParameterizedTest
    void findAllTransactionFilter_shouldReturnTransactionsByCreatedAtTo(long millis) {
        Client client = getDummyClient();
        Transaction transaction = new Transaction();
        transaction.setClient(client);
        transaction.setCreatedAt(Instant.ofEpochMilli(millis - 50000L));
        fillFields(transaction);
        transactionRepository.save(transaction);

        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(new TransactionFilter(null, null, Instant.ofEpochMilli(millis))),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
    }

    @Test
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
    void findAllClientTransactionFilter_shouldReturnTransactionsByCreatedAtFrom(long millis) {
        Client client = getDummyClient();
        Transaction transaction = new Transaction();
        transaction.setClient(client);
        transaction.setCreatedAt(Instant.ofEpochMilli(millis + 10000L));
        fillFields(transaction);
        transactionRepository.save(transaction);

        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(client.getId(), new ClientTransactionFilter(Instant.ofEpochMilli(millis), null)),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
    }

    @ValueSource(longs = {
            1789912612000L, 1789912712302L
    })
    @ParameterizedTest
    void findAllClientTransactionFilter_shouldReturnTransactionsByCreatedAtTo(long millis) {
        Client client = getDummyClient();
        Transaction transaction = new Transaction();
        transaction.setClient(client);
        transaction.setCreatedAt(Instant.ofEpochMilli(millis - 50000L));
        fillFields(transaction);
        transactionRepository.save(transaction);

        Page<TransactionResponse> actual = transactionService.findAll(
                TransactionSpecification.matches(client.getId(), new ClientTransactionFilter(null, Instant.ofEpochMilli(millis))),
                PageRequest.of(0, 20),
                TransactionResponse.class
        );

        assertEquals(1, actual.getTotalElements());
    }
}