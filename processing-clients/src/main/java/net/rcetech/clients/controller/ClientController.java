package net.rcetech.clients.controller;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.clients.event.KeycloakEvent;
import net.rcetech.clients.service.KeycloakEventService;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.clients.dto.ClientFilter;
import net.rcetech.meta.clients.projection.ClientProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequestMapping("/client")
public class ClientController {

    private final KeycloakEventService keycloakEventService;

    private final ClientService clientService;

    public ClientController(KeycloakEventService keycloakEventService, ClientService clientService) {
        this.keycloakEventService = keycloakEventService;
        this.clientService = clientService;
    }

    @PostMapping("/event/")
    @ResponseStatus(HttpStatus.CREATED)
    public void event(@RequestBody KeycloakEvent event) {
        log.trace("Получен ивент из keycloak: {}", event);
        keycloakEventService.handle(event);
    }

    @GetMapping
    public PagedModel<ClientProjection> getClients(ClientFilter filter, Pageable pageable) {
        return new PagedModel<>(clientService.findAll(filter, pageable));
    }
}
