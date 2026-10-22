package net.rcetech.api.config;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import net.rcetech.meta.clients.dto.ClientPrincipal;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Objects;

@EqualsAndHashCode(callSuper = true)
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final transient ClientPrincipal principal;

    private final String credentials;

    public ApiKeyAuthenticationToken(String credentials) {
        super(java.util.List.of());
        this.credentials = credentials;
        this.principal = null;
        setAuthenticated(false);
    }

    public ApiKeyAuthenticationToken(ClientPrincipal principal, String credentials, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.credentials = credentials;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    @NonNull
    public String getName() {
        return Objects.requireNonNull(principal.id()).toString();
    }
}
