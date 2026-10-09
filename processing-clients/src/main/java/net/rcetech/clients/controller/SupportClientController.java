package net.rcetech.clients.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.clients.dto.ClientFilter;
import net.rcetech.meta.clients.dto.UpdateClientDTO;
import net.rcetech.meta.clients.projection.SupportClientProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Slf4j
@RequestMapping(WebPath.PRIVATE_API_PATH + "/client")
@Validated
public class SupportClientController {

    private final ClientService clientService;

    public SupportClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PagedModel<SupportClientProjection> getClients(ClientFilter filter,
                                                          @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(clientService.findAll(filter, pageable, SupportClientProjection.class));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SupportClientProjection> update(@PathVariable UUID id, @Valid @RequestBody UpdateClientDTO updateClientDTO) {
        return new ResponseEntity<>(clientService.update(id, updateClientDTO), HttpStatus.OK);
    }
}
