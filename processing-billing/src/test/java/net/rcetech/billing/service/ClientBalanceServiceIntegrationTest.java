package net.rcetech.billing.service;

import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.model.billing.Transaction;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.billing.TransactionRepository;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.service.billing.TransactionService;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.domain.service.support.SupportUserService;
import net.rcetech.domain.test.SharedMySQLContainer;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.billing.TransactionType;
import net.rcetech.meta.clients.projection.SupportClientProjection;
import net.rcetech.meta.config.MetaWebConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        OrderService.class, ClientService.class, ClientBalanceService.class, TransactionService.class,
        SupportUserService.class, MetaWebConfig.class
})
class ClientBalanceServiceIntegrationTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SharedMySQLContainer.register(registry);
    }

    @MockitoBean
    private ClientMapper clientMapper;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ClientBalanceService clientBalanceService;

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        clientRepository.deleteAll();
    }

    private Client saveClient(Integer balance) {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test" + client.getId());
        client.setRegisteredAt(Instant.now());
        client.setBalance(balance);
        return clientRepository.save(client);
    }

    private void saveTransaction(Client client, Operation operation, Integer amount) {
        Transaction transaction = new Transaction();
        transaction.setClient(client);
        transaction.setCreatedAt(Instant.now());
        transaction.setType(TransactionType.MANUAL_CORRECT);
        transaction.setOperation(operation);
        transaction.setAmount(amount);
        transaction.setComment("Транзакция создана тестом.");
        transactionRepository.save(transaction);
    }

    @ParameterizedTest
    @CsvSource({
            "0,5000,1200",
            "999999,300,300",
            "70,48000,15"
    })
    @DisplayName("Проекция, возвращённая после пересчёта, должна содержать пересчитанный баланс.")
    void recalculate_shouldReturnProjectionWithRecalculatedBalance(Integer staleBalance, Integer credit, Integer debit) {
        Client client = saveClient(staleBalance);
        assertNotNull(client.getId());
        saveTransaction(client, Operation.CREDIT, credit);
        saveTransaction(client, Operation.DEBIT, debit);

        SupportClientProjection actual = clientBalanceService.recalculate(client.getId(), SupportClientProjection.class);

        assertAll(
                () -> assertEquals(client.getId(), actual.getId()),
                () -> assertEquals(credit - debit, actual.getBalance()),
                () -> assertEquals(credit - debit, clientRepository.findById(client.getId()).orElseThrow().getBalance())
        );
    }
}
