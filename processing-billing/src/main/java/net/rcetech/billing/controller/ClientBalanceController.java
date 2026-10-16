package net.rcetech.billing.controller;

import net.rcetech.billing.service.ClientBalanceService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.clients.projection.SupportClientProjection;
import net.rcetech.meta.user.SupportAccess;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(WebPath.PRIVATE_API_PATH + "/client")
@SupportAccess
public class ClientBalanceController {

    private final ClientBalanceService clientBalanceService;

    public ClientBalanceController(ClientBalanceService clientBalanceService) {
        this.clientBalanceService = clientBalanceService;
    }

    @PostMapping("/{id}/balance/recalculation")
    public SupportClientProjection recalculate(@PathVariable UUID id) {
        return clientBalanceService.recalculate(id, SupportClientProjection.class);
    }
}
