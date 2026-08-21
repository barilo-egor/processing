package net.rcetech.clients.controller;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.clients.event.KeycloakEvent;
import net.rcetech.clients.service.KeycloakEventService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequestMapping("/client")
public class ClientController {

    private final KeycloakEventService keycloakEventService;

    public ClientController(KeycloakEventService keycloakEventService) {
        this.keycloakEventService = keycloakEventService;
    }

    @PostMapping("/event/")
    @ResponseStatus(HttpStatus.CREATED)
    public void event(@RequestBody KeycloakEvent event) {
        log.trace("Получен ивент из keycloak: {}", event);
        keycloakEventService.handle(event);
    }
}
