package net.rcetech.security;

import jakarta.servlet.http.HttpServletResponse;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.support.SupportUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationHandlerTest {

    @Mock
    private SupportUserService supportUserService;

    @Mock
    private ClientService clientService;

    @InjectMocks
    private AuthenticationHandler authenticationHandler;

    @ParameterizedTest
    @CsvSource({
            "5560783c-ad7f-4395-b734-74b2ceaf5897,ROLE_TRADER",
            "1d1b97fd-f4f9-4049-86ea-10f5379eaca1,ROLE_UNKNOWN"
    })
    @DisplayName("Метод должен пропустить аутентификацию, если роль не определена.")
    void onAuthenticationSuccess_shouldSkipIfUnknownRole(UUID name, String role) throws IOException {
        Collection<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        GrantedAuthority grantedAuthority = mock(GrantedAuthority.class);
        when(grantedAuthority.getAuthority()).thenReturn(role);
        grantedAuthorities.add(grantedAuthority);
        Authentication authentication = new org.springframework.security.authentication.TestingAuthenticationToken(
                name.toString(),
                null,
                grantedAuthorities
        );
        HttpServletResponse response = mock(HttpServletResponse.class);

        authenticationHandler.onAuthenticationSuccess(null, response, authentication);

        verify(supportUserService, times(0)).createIfNotExists(any(), anyString());
        verify(clientService, times(0)).createIfNotExists(any(), anyString());
        verify(response, times(0)).sendRedirect(anyString());
    }

    @ParameterizedTest
    @CsvSource({
            "5560783c-ad7f-4395-b734-74b2ceaf5897,ROLE_ADMIN,test1",
            "1d1b97fd-f4f9-4049-86ea-10f5379eaca1,ROLE_OPERATOR,smokilolik"
    })
    @DisplayName("Метод должен создать запись пользователя поддержки.")
    void onAuthenticationSuccess_shouldCreateSupportUserIfAdminOrOperator(UUID name, String role, String username) throws IOException {
        Collection<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        GrantedAuthority grantedAuthority = mock(GrantedAuthority.class);
        when(grantedAuthority.getAuthority()).thenReturn(role);
        grantedAuthorities.add(grantedAuthority);
        Map<String, Object> claims = Map.of(
                "sub", name.toString(),
                "iss", "https://example.com",
                "preferred_username", username
        );
        OidcIdToken idToken = new OidcIdToken("token-value-abc", Instant.now(),
                Instant.now().plusSeconds(3600), claims);
        OidcUserInfo userInfo = new OidcUserInfo(claims);
        DefaultOidcUser oidcUser = new DefaultOidcUser(grantedAuthorities, idToken, userInfo);
        Authentication authentication = new org.springframework.security.authentication.TestingAuthenticationToken(
                oidcUser,
                null,
                grantedAuthorities
        );
        HttpServletResponse response = mock(HttpServletResponse.class);

        authenticationHandler.onAuthenticationSuccess(null, response, authentication);

        verify(supportUserService).createIfNotExists(name, username);
        verify(clientService, times(0)).createIfNotExists(any(), anyString());
        verify(response).sendRedirect("/");
    }


    @ParameterizedTest
    @CsvSource({
            "5560783c-ad7f-4395-b734-74b2ceaf5897,ROLE_CLIENT,tgshop",
            "1d1b97fd-f4f9-4049-86ea-10f5379eaca1,ROLE_CLIENT,terminator"
    })
    @DisplayName("Метод должен создать запись клиента.")
    void onAuthenticationSuccess_shouldCreateClientIfRoleClient(UUID name, String role, String username) throws IOException {
        Collection<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        GrantedAuthority grantedAuthority = mock(GrantedAuthority.class);
        when(grantedAuthority.getAuthority()).thenReturn(role);
        grantedAuthorities.add(grantedAuthority);
        Map<String, Object> claims = Map.of(
                "sub", name.toString(),
                "iss", "https://example.com",
                "preferred_username", username
        );
        OidcIdToken idToken = new OidcIdToken("token-value-abc", Instant.now(),
                Instant.now().plusSeconds(3600), claims);
        OidcUserInfo userInfo = new OidcUserInfo(claims);
        DefaultOidcUser oidcUser = new DefaultOidcUser(grantedAuthorities, idToken, userInfo);
        Authentication authentication = new org.springframework.security.authentication.TestingAuthenticationToken(
                oidcUser,
                null,
                grantedAuthorities
        );
        HttpServletResponse response = mock(HttpServletResponse.class);

        authenticationHandler.onAuthenticationSuccess(null, response, authentication);

        verify(supportUserService, times(0)).createIfNotExists(any(), any());
        verify(clientService).createIfNotExists(name, username);
        verify(response).sendRedirect("/");
    }

}