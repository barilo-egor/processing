package net.rcetech.clients.controller;

import jakarta.validation.constraints.NotBlank;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.service.clients.ApiKeyService;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.clients.dto.ApiKeyResponseDTO;
import net.rcetech.meta.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping(WebPath.PRIVATE_API_PATH + "/api-key")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    private final ClientService clientService;

    public ApiKeyController(ApiKeyService apiKeyService, ClientService clientService) {
        this.apiKeyService = apiKeyService;
        this.clientService = clientService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<String> createApiKey(@RequestParam @NotBlank String name, Principal principal) {
        return new ResponseEntity<>(apiKeyService.create(name, getClient(principal)), HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<ApiKeyResponseDTO>> getApiKey(Principal principal) {
        return new ResponseEntity<>(apiKeyService.findAllByClientId(getClient(principal)), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<ApiKeyResponseDTO>> delete(Principal principal, @PathVariable Long id) {
        apiKeyService.delete(getClient(principal), id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    private Client getClient(Principal principal) {
        Optional<Client> client = clientService.findById(UUID.fromString(principal.getName()));
        if (client.isEmpty()) {
            throw new BaseException("Не найден клиент по идентификатору из контекста.");
        }
        return client.get();
    }
}
