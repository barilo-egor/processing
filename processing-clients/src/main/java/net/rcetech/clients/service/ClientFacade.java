package net.rcetech.clients.service;

import jakarta.persistence.OptimisticLockException;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.clients.dto.ClientUpdateRequest;
import net.rcetech.meta.clients.dto.UpdateClientDTO;
import net.rcetech.meta.clients.projection.ClientProjection;
import net.rcetech.meta.clients.projection.SupportClientProjection;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Retryable(value = OptimisticLockException.class, delay = 500, jitter = 100)
public class ClientFacade {

    private final ClientService clientService;

    public ClientFacade(ClientService clientService) {
        this.clientService = clientService;
    }

    public ClientProjection update(UUID id, ClientUpdateRequest clientUpdateRequest) {
        return clientService.update(id, clientUpdateRequest);
    }

    public SupportClientProjection update(UUID id, UpdateClientDTO updateClientDTO) {
        return clientService.update(id, updateClientDTO);
    }
}
