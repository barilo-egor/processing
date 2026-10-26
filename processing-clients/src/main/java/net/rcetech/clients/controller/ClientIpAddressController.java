package net.rcetech.clients.controller;

import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.clients.IpAddressService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.clients.dto.IpAddressResponseDTO;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.user.ClientAccess;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping(WebPath.CLIENT_API_PATH + "/ip-address")
public class ClientIpAddressController {

    private final IpAddressService ipAddressService;

    private final ClientService clientService;

    public ClientIpAddressController(IpAddressService ipAddressService, ClientService clientService) {
        this.ipAddressService = ipAddressService;
        this.clientService = clientService;
    }

    @PostMapping
    @ClientAccess
    public ResponseEntity<IpAddressResponseDTO> create(@RequestParam @NotBlank String address, Principal principal) {
        IpAddressResponseDTO ipAddress = ipAddressService.create(address, getClient(principal));
        log.info("Клиент {} добавил IP-адрес {} в белый список.", principal.getName(), ipAddress.address());
        return new ResponseEntity<>(ipAddress, HttpStatus.CREATED);
    }

    @GetMapping
    @ClientAccess
    public ResponseEntity<List<IpAddressResponseDTO>> getAll(Principal principal) {
        return new ResponseEntity<>(ipAddressService.findAllByClientId(getClient(principal)), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @ClientAccess
    public ResponseEntity<Void> delete(Principal principal, @PathVariable Long id) {
        ipAddressService.delete(getClient(principal), id);
        log.info("Клиент {} удалил IP-адрес {} из белого списка.", principal.getName(), id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    private Client getClient(Principal principal) {
        return clientService.findById(UUID.fromString(principal.getName()))
                .orElseThrow(() -> new BaseException("Не найден клиент по идентификатору из контекста."));
    }
}
