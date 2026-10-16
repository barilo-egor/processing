package net.rcetech.support.controller;

import net.rcetech.domain.service.support.SupportUserService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.exception.ServiceUnavailableException;
import net.rcetech.meta.support.dto.SupportUserResponse;
import net.rcetech.meta.user.SupportAccess;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping(WebPath.SUPPORT_API_PATH + "/support-user")
@SupportAccess
public class SupportUserController {

    private final SupportUserService supportUserService;

    public SupportUserController(SupportUserService supportUserService) {
        this.supportUserService = supportUserService;
    }

    @GetMapping
    public SupportUserResponse get(Principal principal) {
        return supportUserService.findById(UUID.fromString(principal.getName()), SupportUserResponse.class)
                .orElseThrow(() -> new ServiceUnavailableException("Не найден пользователь поддержки."));
    }
}
