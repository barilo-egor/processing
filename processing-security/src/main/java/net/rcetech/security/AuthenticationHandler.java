package net.rcetech.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

@Component(value = "registrationAuthenticationHandler")
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
            supportUserService.createIfNotExists(UUID.fromString(authentication.getName()),
                    ((OidcUser) Objects.requireNonNull(authentication.getPrincipal())).getAttribute("preferred_username"));
            response.sendRedirect("/");
        } else if (roles.contains(Role.CLIENT.withPrefix())) {
            clientService.createIfNotExists(
                    UUID.fromString(authentication.getName()),
                    ((OidcUser) Objects.requireNonNull(authentication.getPrincipal())).getAttribute("preferred_username")
            );
            response.sendRedirect("/");
        }
    }
}
