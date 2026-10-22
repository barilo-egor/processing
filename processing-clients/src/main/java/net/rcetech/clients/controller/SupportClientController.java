package net.rcetech.clients.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.service.clients.ClientDeletionService;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.clients.dto.ClientFilter;
import net.rcetech.meta.clients.dto.UpdateClientDTO;
import net.rcetech.meta.clients.projection.SupportClientProjection;
import net.rcetech.meta.user.AdminAccess;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@Slf4j
@RequestMapping(WebPath.SUPPORT_API_PATH + "/client")
@Validated
public class SupportClientController {

    private final ClientService clientService;

    private final ClientDeletionService clientDeletionService;

    public SupportClientController(ClientService clientService, ClientDeletionService clientDeletionService) {
        this.clientService = clientService;
        this.clientDeletionService = clientDeletionService;
    }

    @GetMapping
    @AdminAccess
    public PagedModel<SupportClientProjection> getClients(ClientFilter filter,
                                                          @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(clientService.findAll(filter, pageable, SupportClientProjection.class));
    }

    @PatchMapping("/{id}")
    @AdminAccess
    public ResponseEntity<SupportClientProjection> update(Principal principal, @PathVariable UUID id,
                                                          @Valid @RequestBody UpdateClientDTO updateClientDTO) {
        SupportClientProjection client = clientService.update(id, updateClientDTO);
        log.info("Сотрудник поддержки {} обновил настройки клиента {}: {}.", principal.getName(), id, updateClientDTO);
        return new ResponseEntity<>(client, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @AdminAccess
    public ResponseEntity<Void> delete(Principal principal, @PathVariable UUID id) {
        clientDeletionService.delete(id);
        log.info("Сотрудник поддержки {} удалил клиента {}.", principal.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
