package net.rcetech.clients.controller;

import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.clients.dto.ClientResponseDTO;
import net.rcetech.meta.exception.BaseException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ClientResponseDTO get(Principal principal) {
        return clientService.findById(UUID.fromString(principal.getName()), ClientResponseDTO.class)
                .orElseThrow(() -> new BaseException(
                        "Клиент не найден в базе данных по своему principal.getName(): " + principal.getName())
                );
    }
}
