package net.rcetech.meta.user;

import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@NullMarked
public class KeycloakRoleConverter implements GrantedAuthoritiesMapper {

    private static final String REALM_ACCESS_KEY = "realm_access";
    private static final String ROLES_KEY = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .flatMap(this::extractAuthorities)
                .collect(Collectors.toSet());
    }

    private Stream<GrantedAuthority> extractAuthorities(GrantedAuthority authority) {
        if (!(authority instanceof OidcUserAuthority oidcAuthority)) {
            return Stream.of(authority);
        }

        Map<String, Object> claims = oidcAuthority.getIdToken().getClaims();

        if (claims.get(REALM_ACCESS_KEY) instanceof Map<?, ?> realmAccessMap
                && realmAccessMap.get(ROLES_KEY) instanceof Collection<?> rolesCollection) {

            Stream<GrantedAuthority> realmRoles = rolesCollection.stream()
                    .filter(String.class::isInstance)
                    .map(Object::toString)
                    .map(this::cleanAndPrefixRole)
                    .map(SimpleGrantedAuthority::new);

            return Stream.concat(Stream.of(oidcAuthority), realmRoles);
        }

        return Stream.of(oidcAuthority);
    }

    private String cleanAndPrefixRole(String role) {
        String upperRole = role.toUpperCase(Locale.ROOT);
        return upperRole.startsWith(ROLE_PREFIX) ? upperRole : ROLE_PREFIX + upperRole;
    }
}
