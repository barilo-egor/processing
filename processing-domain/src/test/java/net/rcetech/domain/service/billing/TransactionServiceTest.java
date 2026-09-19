package net.rcetech.domain.service.billing;

import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.model.billing.Transaction;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.billing.TransactionRepository;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.billing.TransactionType;
import net.rcetech.meta.billing.dto.ManualCorrectTransaction;
import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.meta.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Instant;
import java.util.List;
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
}