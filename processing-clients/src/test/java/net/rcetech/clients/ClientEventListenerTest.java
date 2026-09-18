package net.rcetech.clients;

import net.rcetech.clients.service.ClientBalanceService;
import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.model.billing.Transaction;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.billing.TransactionRepository;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.service.billing.TransactionService;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.billing.TransactionCreatedEvent;
import net.rcetech.meta.billing.TransactionType;
import net.rcetech.meta.clients.ClientStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OrderService.class, ClientService.class, ClientBalanceService.class, ClientEventListener.class, TransactionService.class})
class ClientEventListenerTest {

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
        registry.add("spring.flyway.locations", () -> "classpath:database/migration");
    }

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ClientRepository clientRepository;

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
    @CsvSource("""
            ORDER_CONFIRMATION,500
            MANUAL_CORRECT,489990
            MANUAL_CORRECT,1
            """)
    void transactionCreated_shouldCreditBalance(TransactionType transactionType, Integer amount) {
        Client client = getDummyClient();
        Transaction transaction = new Transaction();
        transaction.setClient(client);
        transaction.setCreatedAt(Instant.now());
        transaction.setType(transactionType);
        transaction.setOperation(Operation.CREDIT);
        transaction.setAmount(amount);
        transaction.setComment("Подтверждение по ордеру 123. Транзакция создана системой.");
        transactionRepository.save(transaction);
        assertEquals(0, client.getBalance());
        TransactionCreatedEvent event = new TransactionCreatedEvent(this, transaction.getId());
        eventPublisher.publishEvent(event);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        assertNotNull(client.getId());
        Optional<Client> maybeClient = clientRepository.findById(client.getId());
        assertTrue(maybeClient.isPresent());
        assertEquals(amount, maybeClient.get().getBalance());
    }

    @ParameterizedTest
    @CsvSource("""
            CLIENT_WITHDRAWAL,250
            MANUAL_CORRECT,4006
            MANUAL_CORRECT,2066
            """)
    void transactionCreated_shouldDebitBalance(TransactionType transactionType, Integer amount) {
        Client client = getDummyClient();
        client.setBalance(amount + 1000);
        Transaction transaction = new Transaction();
        transaction.setClient(client);
        transaction.setCreatedAt(Instant.now());
        transaction.setType(transactionType);
        transaction.setOperation(Operation.DEBIT);
        transaction.setAmount(amount);
        transaction.setComment("Подтверждение заявки на вывод 123. Транзакция создана системой.");
        transactionRepository.save(transaction);
        TransactionCreatedEvent event = new TransactionCreatedEvent(this, transaction.getId());
        eventPublisher.publishEvent(event);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        assertNotNull(client.getId());
        Optional<Client> maybeClient = clientRepository.findById(client.getId());
        assertTrue(maybeClient.isPresent());
        assertEquals(1000, maybeClient.get().getBalance());
    }

}