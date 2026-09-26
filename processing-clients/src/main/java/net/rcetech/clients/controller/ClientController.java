package net.rcetech.clients.controller;

import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.clients.dto.ClientUpdateRequest;
import net.rcetech.meta.clients.projection.ClientProjection;
import net.rcetech.meta.exception.BaseException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping(WebPath.V1_API_PATH + "/client")
@PreAuthorize("hasRole('CLIENT')")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    public ClientProjection get(Principal principal) {
        return clientService.findById(UUID.fromString(principal.getName()), ClientProjection.class)
                .orElseThrow(() -> new BaseException(
                        "Клиент не найден в базе данных по своему principal.getName(): " + principal.getName())
                );
    }

    @PatchMapping
    public ClientProjection patch(Principal principal, @RequestBody ClientUpdateRequest clientUpdateRequest) {
        return clientService.update(UUID.fromString(principal.getName()), clientUpdateRequest);
    }
}
