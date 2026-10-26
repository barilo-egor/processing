package net.rcetech.orders.controller;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.user.SupportAccess;
import net.rcetech.orders.callback.CallbackDispatcher;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping(WebPath.SUPPORT_API_PATH + "/client-callback")
@SupportAccess
public class ClientCallbackController {

    private final CallbackDispatcher callbackDispatcher;

    public ClientCallbackController(CallbackDispatcher callbackDispatcher) {
        this.callbackDispatcher = callbackDispatcher;
    }

    @PostMapping("/{orderId}")
    public void resend(Principal principal, @PathVariable UUID orderId) {
        callbackDispatcher.resend(orderId);
        log.info("Сотрудник поддержки {} повторно отправил клиенту КБ по ордеру {}.", principal.getName(), orderId);
    }
}
