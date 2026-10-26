package net.rcetech.api.controller;

import jakarta.servlet.http.Cookie;
import net.rcetech.api.service.OrderApiService;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.service.clients.ApiKeyService;
import net.rcetech.domain.test.SharedMySQLContainer;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClientOrderControllerSecurityTest {

    private static final String BASE_PATH = WebPath.CLIENT_API_PATH + "/order";

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private OrderApiService orderApiService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SharedMySQLContainer.register(registry);
    }

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(orderApiService.findAll(any(), any(), any())).thenReturn(Page.empty());
    }

    private Client createClient() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test" + client.getId());
        client.setRegisteredAt(Instant.now());
        client.setIpRestrictionEnabled(false);
        return clientRepository.save(client);
    }

    private static MockHttpSession sessionOf(UUID userId, Role role) {
        MockHttpSession session = new MockHttpSession();
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                userId.toString(), null, List.of(new SimpleGrantedAuthority(role.withPrefix())));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication));
        return session;
    }

    private static Stream<OrderEndpoint> clientEndpoints() {
        return Arrays.stream(OrderEndpoint.values())
                .filter(endpoint -> endpoint != OrderEndpoint.CREATE);
    }

    static Stream<Arguments> endpointsAndCsrfTokens() {
        return clientEndpoints()
                .flatMap(endpoint -> Stream.of(
                        Arguments.of(endpoint, UUID.randomUUID().toString()),
                        Arguments.of(endpoint, UUID.randomUUID().toString())
                ));
    }

    static Stream<Arguments> modifyingEndpointsAndCsrfHeaders() {
        return clientEndpoints()
                .filter(OrderEndpoint::isModifying)
                .flatMap(endpoint -> Stream.of(
                        Arguments.of(endpoint, null),
                        Arguments.of(endpoint, "forged-token")
                ));
    }

    static Stream<Arguments> endpointsAndKeyNames() {
        return clientEndpoints()
                .flatMap(endpoint -> Stream.of(
                        Arguments.of(endpoint, "key"),
                        Arguments.of(endpoint, "integration-key")
                ));
    }

    static Stream<Arguments> endpointsAndSupportRoles() {
        return clientEndpoints()
                .flatMap(endpoint -> Role.SUPPORT_USERS.stream()
                        .map(role -> Arguments.of(endpoint, role)));
    }

    @ParameterizedTest
    @MethodSource("endpointsAndCsrfTokens")
    @DisplayName("Запрос с сессией клиента и валидным CSRF токеном выполняется от имени клиента сессии.")
    void clientSession_withCsrf_shouldAuthorizeSessionClient(OrderEndpoint endpoint, String csrfToken)
            throws Exception {
        UUID clientId = createClient().getId();
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(endpoint.request(BASE_PATH, orderId)
                        .session(sessionOf(clientId, Role.CLIENT))
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().is(endpoint.successStatus()));

        endpoint.verifyCalled(orderApiService, clientId, orderId);
    }

    @ParameterizedTest
    @MethodSource("modifyingEndpointsAndCsrfHeaders")
    @DisplayName("Изменяющий запрос с сессией клиента без валидного CSRF заголовка получает 403.")
    void clientSession_withoutValidCsrfHeader_shouldReturnForbidden(OrderEndpoint endpoint, String csrfHeader)
            throws Exception {
        UUID clientId = createClient().getId();
        var request = endpoint.request(BASE_PATH, UUID.randomUUID())
                .session(sessionOf(clientId, Role.CLIENT))
                .cookie(new Cookie("XSRF-TOKEN", UUID.randomUUID().toString()));
        if (csrfHeader != null) {
            request.header("X-XSRF-TOKEN", csrfHeader);
        }

        mockMvc.perform(request)
                .andExpect(status().isForbidden());

        verifyNoInteractions(orderApiService);
    }

    @ParameterizedTest
    @MethodSource("endpointsAndKeyNames")
    @DisplayName("Запрос с валидным апи ключом без сессии получает 401.")
    void apiKey_withoutSession_shouldReturnUnauthorized(OrderEndpoint endpoint, String keyName) throws Exception {
        Client client = createClient();
        String apiKey = apiKeyService.create(keyName, client);
        String csrfToken = UUID.randomUUID().toString();

        mockMvc.perform(endpoint.request(BASE_PATH, UUID.randomUUID())
                        .header("Api-Key", apiKey)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderApiService);
    }

    @ParameterizedTest
    @MethodSource("endpointsAndSupportRoles")
    @DisplayName("Запрос с сессией пользователя поддержки получает 403.")
    void supportUserSession_shouldReturnForbidden(OrderEndpoint endpoint, Role role) throws Exception {
        String csrfToken = UUID.randomUUID().toString();

        mockMvc.perform(endpoint.request(BASE_PATH, UUID.randomUUID())
                        .session(sessionOf(UUID.randomUUID(), role))
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isForbidden());

        verifyNoInteractions(orderApiService);
    }
}
