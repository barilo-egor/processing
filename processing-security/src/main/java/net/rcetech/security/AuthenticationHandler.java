package net.rcetech.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.support.SupportUserService;
import net.rcetech.meta.user.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AuthenticationHandler implements AuthenticationSuccessHandler {

    private final SupportUserService supportUserService;

    private final ClientService clientService;

    public AuthenticationHandler(SupportUserService supportUserService, ClientService clientService) {
        this.supportUserService = supportUserService;
        this.clientService = clientService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        if (roles.stream().anyMatch(Role.SUPPORT_USERS_WITH_PREFIX::contains)) {
            String username = getUsername(authentication);
            supportUserService.createIfNotExists(UUID.fromString(authentication.getName()), username);
            log.info("Вход сотрудника поддержки {} ({}), роли {}.", authentication.getName(), username, roles);
            response.sendRedirect("/");
        } else if (roles.contains(Role.CLIENT.withPrefix())) {
            String username = getUsername(authentication);
            clientService.createIfNotExists(UUID.fromString(authentication.getName()), username);
            log.info("Вход клиента {} ({}).", authentication.getName(), username);
            response.sendRedirect("/");
        } else {
            log.info("Вход пользователя {} без ролей.", authentication.getName());
            response.sendRedirect("/");
        }
    }

    private String getUsername(Authentication authentication) {
        return ((OidcUser) Objects.requireNonNull(authentication.getPrincipal())).getAttribute("preferred_username");
    }
}
