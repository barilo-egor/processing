package net.rcetech.clients.service;

import jakarta.persistence.OptimisticLockException;
import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.billing.TransactionRepository;
import net.rcetech.domain.service.billing.TransactionService;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.clients.dto.ClientUpdateRequest;
import net.rcetech.meta.clients.dto.UpdateClientDTO;
import net.rcetech.meta.clients.projection.ClientProjection;
import net.rcetech.meta.clients.projection.SupportClientProjection;
import net.rcetech.meta.config.MetaExecutorSpringConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {
        ClientFacade.class, TransactionService.class, MetaExecutorSpringConfig.class
})
class ClientFacadeTest {

    @MockitoBean
    private ClientService clientService;

    @MockitoBean
    private ClientMapper clientMapper;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private TransactionRepository transactionRepository;

    @MockitoBean
    private TransactionService transactionService;

    @Autowired
    private ClientFacade clientFacade;

    @ParameterizedTest
    @CsvSource({
            "23152,16af5227-d690-424f-9e36-89fd8b955146,70664",
            "3509,77fb78d1-e61f-4522-847e-4533f703f8dc,2105"
    })
    @DisplayName("Метод должен повторить вызов пополнения баланса 3 раза при оптимистической блокировке.")
    void updateBalanceAfterTransaction_shouldRepeatCreditIfOptimisticLockExceptionThrown(Long id, UUID clientId,
                                                                                         Integer amount) {
        when(transactionService.findById(any(), eq(ClientFacade.TransactionProjection.class)))
                .thenReturn(Optional.of(new ClientFacade.TransactionProjection(id, clientId, Operation.CREDIT, amount)));
        Client client = new Client();
        client.setId(clientId);
        doThrow(OptimisticLockException.class)
                .doThrow(OptimisticLockException.class)
                .doNothing()
                .when(clientService).creditBalance(any(), any());

        clientFacade.updateBalanceAfterTransaction(id);

        verify(clientService, times(3)).creditBalance(clientId, amount);
    }

    @ParameterizedTest
    @CsvSource({
            "23152,16af5227-d690-424f-9e36-89fd8b955146,70664",
            "3509,77fb78d1-e61f-4522-847e-4533f703f8dc,2105"
    })
    @DisplayName("Метод должен повторить вызов снятия с баланса 3 раза при оптимистической блокировке.")
    void updateBalanceAfterTransaction_shouldRepeatDebitIfOptimisticLockExceptionThrown(Long id, UUID clientId,
                                                                                        Integer amount) {
        when(transactionService.findById(any(), eq(ClientFacade.TransactionProjection.class)))
                .thenReturn(Optional.of(new ClientFacade.TransactionProjection(id, clientId, Operation.DEBIT, amount)));
        Client client = new Client();
        client.setId(clientId);
        doThrow(OptimisticLockException.class)
                .doThrow(OptimisticLockException.class)
                .doNothing()
                .when(clientService).debitBalance(any(), any());

        clientFacade.updateBalanceAfterTransaction(id);

        verify(clientService, times(3)).debitBalance(clientId, amount);
    }

    @Test
    @DisplayName("Метод должен повторить обновление клиента 3 раза, если был брошен OptimisticLockException.")
    void update_shouldRepeatUpdateClientUpdateRequestIfOptimisticLockExceptionThrown() {
        ClientProjection clientProjection = mock(ClientProjection.class);
        when(clientService.update(any(), any(ClientUpdateRequest.class)))
                .thenThrow(OptimisticLockException.class)
                .thenThrow(OptimisticLockException.class)
                .thenReturn(clientProjection);

        ClientProjection actual = clientFacade.update(UUID.randomUUID(), new ClientUpdateRequest("https://example.com/callback"));

        verify(clientService, times(3)).update(any(), any(ClientUpdateRequest.class));
        assertEquals(clientProjection, actual);
    }

    @Test
    @DisplayName("Метод должен повторить обновление клиента 3 раза, если был брошен OptimisticLockException.")
    void update_shouldRepeatUpdateUpdateClientDTOIfOptimisticLockExceptionThrown() {
        SupportClientProjection supportClientProjection = mock(SupportClientProjection.class);
        when(clientService.update(any(), any(UpdateClientDTO.class)))
                .thenThrow(OptimisticLockException.class)
                .thenThrow(OptimisticLockException.class)
                .thenReturn(supportClientProjection);

        SupportClientProjection actual = clientFacade.update(UUID.randomUUID(), new UpdateClientDTO(
                null, null, null, null, null
                )
        );

        verify(clientService, times(3)).update(any(), any(UpdateClientDTO.class));
        assertEquals(supportClientProjection, actual);
    }
}