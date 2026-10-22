package net.rcetech.clients.service;

import jakarta.persistence.OptimisticLockException;
import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.clients.dto.ClientUpdateRequest;
import net.rcetech.meta.clients.dto.UpdateClientDTO;
import net.rcetech.meta.clients.projection.ClientProjection;
import net.rcetech.meta.clients.projection.SupportClientProjection;
import net.rcetech.meta.config.MetaExecutorSpringConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {
        ClientFacade.class, MetaExecutorSpringConfig.class
})
class ClientFacadeTest {

    @MockitoBean
    private ClientService clientService;

    @MockitoBean
    private ClientMapper clientMapper;

    @Autowired
    private ClientFacade clientFacade;

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