package net.rcetech.security;

import net.rcetech.meta.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRoleConverterTest {

    private final KeycloakRoleConverter converter = new KeycloakRoleConverter();

    @ParameterizedTest
    @CsvSource({
            "SCOPE_openid,ROLE_ADMIN",
            "SCOPE_profile,FACTOR_PASSWORD"
    })
    @DisplayName("Метод должен вернуть не-OIDC полномочия без изменений.")
    void mapAuthorities_shouldKeepNonOidcAuthorities(String first, String second) {
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(first),
                new SimpleGrantedAuthority(second)
        );

        Collection<GrantedAuthority> result = List.copyOf(converter.mapAuthorities(authorities));

        assertThat(result).containsExactlyInAnyOrderElementsOf(authorities);
    }

    @ParameterizedTest
    @MethodSource("realmRolesArguments")
    @DisplayName("Метод должен добавить роли реалма в верхнем регистре с префиксом ROLE_.")
    void mapAuthorities_shouldAddPrefixedRealmRoles(List<String> roles, List<String> expectedRoles) {
        OidcUserAuthority oidcAuthority = oidcAuthority(Map.of("realm_access", Map.of("roles", roles)));

        Collection<GrantedAuthority> result = List.copyOf(converter.mapAuthorities(List.of(oidcAuthority)));

        assertThat(result).containsExactlyInAnyOrderElementsOf(expected(oidcAuthority, expectedRoles));
    }

    static Stream<Arguments> realmRolesArguments() {
        return Stream.of(
                Arguments.of(List.of("admin", "operator"), List.of(Role.ADMIN.withPrefix(), Role.OPERATOR.withPrefix())),
                Arguments.of(List.of("Client"), List.of(Role.CLIENT.withPrefix())),
                Arguments.of(List.of("ROLE_admin", "role_client"), List.of(Role.ADMIN.withPrefix(), Role.CLIENT.withPrefix())),
                Arguments.of(List.of("ROLE_OPERATOR", "offline_access"), List.of(Role.OPERATOR.withPrefix(), "ROLE_OFFLINE_ACCESS"))
        );
    }

    @ParameterizedTest
    @CsvSource({
            "admin,ROLE_ADMIN,ADMIN",
            "client,Role_Client,CLIENT"
    })
    @DisplayName("Метод должен убрать дубликаты ролей после нормализации.")
    void mapAuthorities_shouldDeduplicateRoles(String role, String duplicate, Role expectedRole) {
        OidcUserAuthority oidcAuthority = oidcAuthority(Map.of("realm_access", Map.of("roles", List.of(role, duplicate))));

        Collection<GrantedAuthority> result = List.copyOf(converter.mapAuthorities(List.of(oidcAuthority)));

        assertThat(result).containsExactlyInAnyOrderElementsOf(expected(oidcAuthority, List.of(expectedRole.withPrefix())));
    }

    @ParameterizedTest
    @MethodSource("nonStringRolesArguments")
    @DisplayName("Метод должен пропустить роли, не являющиеся строками.")
    void mapAuthorities_shouldSkipNonStringRoles(List<Object> roles, List<String> expectedRoles) {
        OidcUserAuthority oidcAuthority = oidcAuthority(Map.of("realm_access", Map.of("roles", roles)));

        Collection<GrantedAuthority> result = List.copyOf(converter.mapAuthorities(List.of(oidcAuthority)));

        assertThat(result).containsExactlyInAnyOrderElementsOf(expected(oidcAuthority, expectedRoles));
    }

    static Stream<Arguments> nonStringRolesArguments() {
        return Stream.of(
                Arguments.of(List.of("admin", 42), List.of(Role.ADMIN.withPrefix())),
                Arguments.of(List.of(Map.of("name", "client"), true, "operator"), List.of(Role.OPERATOR.withPrefix()))
        );
    }

    @ParameterizedTest
    @MethodSource("missingRolesClaimsArguments")
    @DisplayName("Метод должен вернуть только OIDC-полномочие, если роли реалма отсутствуют или имеют неверный формат.")
    void mapAuthorities_shouldReturnOnlyOidcAuthorityIfRolesMissing(Map<String, Object> claims) {
        OidcUserAuthority oidcAuthority = oidcAuthority(claims);

        Collection<GrantedAuthority> result = List.copyOf(converter.mapAuthorities(List.of(oidcAuthority)));

        assertThat(result).containsExactly(oidcAuthority);
    }

    static Stream<Arguments> missingRolesClaimsArguments() {
        return Stream.of(
                Arguments.of(Map.of()),
                Arguments.of(Map.of("realm_access", "admin")),
                Arguments.of(Map.of("realm_access", List.of("admin"))),
                Arguments.of(Map.of("realm_access", Map.of())),
                Arguments.of(Map.of("realm_access", Map.of("roles", "admin"))),
                Arguments.of(Map.of("realm_access", Map.of("groups", List.of("client"))))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"SCOPE_openid", "SCOPE_email"})
    @DisplayName("Метод должен объединить не-OIDC полномочия с ролями реалма.")
    void mapAuthorities_shouldCombineOidcAndOtherAuthorities(String scope) {
        OidcUserAuthority oidcAuthority = oidcAuthority(Map.of("realm_access", Map.of("roles", List.of("client"))));
        SimpleGrantedAuthority scopeAuthority = new SimpleGrantedAuthority(scope);

        Collection<GrantedAuthority> result = List.copyOf(converter.mapAuthorities(List.of(oidcAuthority, scopeAuthority)));

        assertThat(result).containsExactlyInAnyOrder(
                oidcAuthority,
                scopeAuthority,
                new SimpleGrantedAuthority(Role.CLIENT.withPrefix())
        );
    }

    private static OidcUserAuthority oidcAuthority(Map<String, Object> extraClaims) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put("sub", "5560783c-ad7f-4395-b734-74b2ceaf5897");
        Instant issuedAt = Instant.now();
        OidcIdToken idToken = new OidcIdToken("token", issuedAt, issuedAt.plusSeconds(60), claims);
        return new OidcUserAuthority(idToken);
    }

    private static List<GrantedAuthority> expected(OidcUserAuthority oidcAuthority, List<String> roles) {
        Stream<GrantedAuthority> roleAuthorities = roles.stream().map(SimpleGrantedAuthority::new);
        return Stream.concat(Stream.of(oidcAuthority), roleAuthorities).toList();
    }
}
