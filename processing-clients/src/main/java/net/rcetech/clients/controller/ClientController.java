package net.rcetech.clients.controller;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.clients.event.KeycloakEvent;
import net.rcetech.clients.service.KeycloakEventService;
import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.clients.dto.ClientFilter;
import net.rcetech.meta.clients.dto.ClientResponseDTO;
import net.rcetech.meta.clients.dto.UpdateClientDTO;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Slf4j
@RequestMapping("/client")
public class ClientController {

    private final KeycloakEventService keycloakEventService;

    private final ClientService clientService;

    private final ClientMapper clientMapper;

    public ClientController(KeycloakEventService keycloakEventService, ClientService clientService,
                            ClientMapper clientMapper) {
        this.keycloakEventService = keycloakEventService;
        this.clientService = clientService;
        this.clientMapper = clientMapper;
    }

    @PostMapping("/event/")
    @ResponseStatus(HttpStatus.CREATED)
    public void event(@RequestBody KeycloakEvent event) {
        log.trace("Получен ивент из keycloak: {}", event);
        keycloakEventService.handle(event);
    }

    @GetMapping
    public PagedModel<ClientResponseDTO> getClients(ClientFilter filter, Pageable pageable) {
        return new PagedModel<>(clientService.findAll(filter, pageable));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClientResponseDTO> update(@PathVariable UUID id, @RequestBody UpdateClientDTO updateClientDTO) {
        return new ResponseEntity<>(clientMapper.toResponse(clientService.update(id, updateClientDTO)), HttpStatus.OK);
    }
}
