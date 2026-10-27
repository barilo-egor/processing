package net.rcetech.security;

import jakarta.servlet.ServletException;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.support.SupportUserService;
import net.rcetech.meta.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    @DisplayName("Метод должен перенаправить на главную без создания записей, если роль не определена.")
    void onAuthenticationSuccess_shouldOnlyRedirectIfUnknownRole(UUID name, String role) throws IOException, ServletException {
        Collection<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        GrantedAuthority grantedAuthority = mock(GrantedAuthority.class);
        when(grantedAuthority.getAuthority()).thenReturn(role);
        grantedAuthorities.add(grantedAuthority);
        Authentication authentication = new TestingAuthenticationToken(
                name.toString(),
                null,
                grantedAuthorities
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        authenticationHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        verify(supportUserService, times(0)).createIfNotExists(any(), anyString());
        verify(clientService, times(0)).createIfNotExists(any(), anyString());
        assertEquals("/", response.getRedirectedUrl());
    }

    @ParameterizedTest
    @CsvSource({
            "5560783c-ad7f-4395-b734-74b2ceaf5897,ROLE_TRADER,/support/dashboard,section=incidents",
            "1d1b97fd-f4f9-4049-86ea-10f5379eaca1,ROLE_UNKNOWN,/client/dashboard,doc=orders"
    })
    @DisplayName("Метод должен перенаправить на адрес, сохранённый до входа.")
    void onAuthenticationSuccess_shouldRedirectToSavedRequest(UUID name, String role, String path, String query)
            throws IOException, ServletException {
        GrantedAuthority grantedAuthority = mock(GrantedAuthority.class);
        when(grantedAuthority.getAuthority()).thenReturn(role);
        Authentication authentication = new TestingAuthenticationToken(name.toString(), null,
                List.of(grantedAuthority));
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest savedRequest = new MockHttpServletRequest("GET", path);
        savedRequest.setQueryString(query);
        savedRequest.setSession(session);
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.saveRequest(savedRequest, new MockHttpServletResponse());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/keycloak-bff");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        authenticationHandler.onAuthenticationSuccess(request, response, authentication);

        assertTrue(Objects.requireNonNull(response.getRedirectedUrl())
                .startsWith("http://localhost" + path + "?" + query));
    }

    @ParameterizedTest
    @CsvSource({
            "5560783c-ad7f-4395-b734-74b2ceaf5897,ADMIN,test1",
            "1d1b97fd-f4f9-4049-86ea-10f5379eaca1,OPERATOR,smokilolik"
    })
    @DisplayName("Метод должен создать запись пользователя поддержки.")
    void onAuthenticationSuccess_shouldCreateSupportUserIfAdminOrOperator(UUID name, Role role, String username) throws IOException, ServletException {
        Collection<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        GrantedAuthority grantedAuthority = mock(GrantedAuthority.class);
        when(grantedAuthority.getAuthority()).thenReturn(role.withPrefix());
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
        Authentication authentication = new TestingAuthenticationToken(
                oidcUser,
                null,
                grantedAuthorities
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        authenticationHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        verify(supportUserService).createIfNotExists(name, username);
        verify(clientService, times(0)).createIfNotExists(any(), anyString());
        assertEquals("/", response.getRedirectedUrl());
    }


    @ParameterizedTest
    @CsvSource({
            "5560783c-ad7f-4395-b734-74b2ceaf5897,CLIENT,tgshop",
            "1d1b97fd-f4f9-4049-86ea-10f5379eaca1,CLIENT,terminator"
    })
    @DisplayName("Метод должен создать запись клиента.")
    void onAuthenticationSuccess_shouldCreateClientIfRoleClient(UUID name, Role role, String username) throws IOException, ServletException {
        Collection<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        GrantedAuthority grantedAuthority = mock(GrantedAuthority.class);
        when(grantedAuthority.getAuthority()).thenReturn(role.withPrefix());
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
        Authentication authentication = new TestingAuthenticationToken(
                oidcUser,
                null,
                grantedAuthorities
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        authenticationHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        verify(supportUserService, times(0)).createIfNotExists(any(), any());
        verify(clientService).createIfNotExists(name, username);
        assertEquals("/", response.getRedirectedUrl());
    }

}