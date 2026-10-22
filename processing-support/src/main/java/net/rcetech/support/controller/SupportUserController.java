package net.rcetech.support.controller;

import net.rcetech.domain.service.support.SupportUserService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.exception.ServiceUnavailableException;
import net.rcetech.meta.support.dto.SupportUserResponse;
import net.rcetech.meta.user.Role;
import net.rcetech.meta.user.SupportAccess;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @GetMapping("/roles")
    public Set<Role> getRoles(Authentication authentication) {
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return Arrays.stream(Role.values())
                .filter(role -> authorities.contains(role.withPrefix()))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
    }
}
