package net.rcetech.billing.service;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.service.billing.TransactionService;
import net.rcetech.domain.service.clients.ClientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = ClientBalanceService.class)
class ClientBalanceServiceTest {

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private ClientService clientService;

    @Autowired
    private ClientBalanceService clientBalanceService;

    @ParameterizedTest
    @CsvSource({
            "16af5227-d690-424f-9e36-89fd8b955146,70664",
            "77fb78d1-e61f-4522-847e-4533f703f8dc,-2105",
            "5d0c1c2e-0b6a-4f43-9c1d-3f1f7c2a9e10,0"
    })
    @DisplayName("Метод должен заблокировать клиента до подсчёта суммы транзакций и записать посчитанный баланс.")
    void recalculate_shouldLockClientBeforeCalculatingBalance(UUID clientId, Integer balance) {
        Client client = new Client();
        client.setId(clientId);
        client.setBalance(12345);
        when(clientService.findForUpdate(clientId)).thenReturn(client);
        when(transactionService.calculateBalance(clientId)).thenReturn(balance);

        clientBalanceService.recalculate(clientId);

        InOrder inOrder = inOrder(clientService, transactionService);
        inOrder.verify(clientService).findForUpdate(clientId);
        inOrder.verify(transactionService).calculateBalance(clientId);
        assertEquals(balance, client.getBalance());
    }
}
