package net.rcetech.billing.controller;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.billing.service.ClientBalanceService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.clients.projection.SupportClientProjection;
import net.rcetech.meta.user.SupportAccess;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping(WebPath.SUPPORT_API_PATH + "/client")
@SupportAccess
public class ClientBalanceController {

    private final ClientBalanceService clientBalanceService;

    public ClientBalanceController(ClientBalanceService clientBalanceService) {
        this.clientBalanceService = clientBalanceService;
    }

    @PostMapping("/{id}/balance/recalculation")
    public SupportClientProjection recalculate(Principal principal, @PathVariable UUID id) {
        SupportClientProjection client = clientBalanceService.recalculate(id, SupportClientProjection.class);
        log.info("Сотрудник поддержки {} пересчитал баланс клиента {}.", principal.getName(), id);
        return client;
    }
}
