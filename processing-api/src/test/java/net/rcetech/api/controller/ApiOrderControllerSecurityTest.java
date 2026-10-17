package net.rcetech.api.controller;

import jakarta.servlet.http.Cookie;
import net.rcetech.api.service.OrderApiService;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.service.clients.ApiKeyService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ApiOrderControllerSecurityTest {

    private static final String BASE_PATH = WebPath.V1_API_PATH + "/order";

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private OrderApiService orderApiService;

    @Container
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.0.46");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:database/migration");
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
        when(orderApiService.createOrder(any(), any(), any())).thenReturn(new Order());
    }

    private Client createClient() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test" + client.getId());
        client.setRegisteredAt(Instant.now());
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

    static Stream<Arguments> endpointsAndKeyNames() {
        return Arrays.stream(OrderEndpoint.values())
                .flatMap(endpoint -> Stream.of(
                        Arguments.of(endpoint, "key"),
                        Arguments.of(endpoint, "integration-key")
                ));
    }

    static Stream<Arguments> endpointsAndRoles() {
        return Arrays.stream(OrderEndpoint.values())
                .flatMap(endpoint -> Arrays.stream(Role.values())
                        .map(role -> Arguments.of(endpoint, role)));
    }

    static Stream<Arguments> endpointsAndInvalidKeys() {
        return Arrays.stream(OrderEndpoint.values())
                .flatMap(endpoint -> Stream.of(
                        Arguments.of(endpoint, "invalid-key"),
                        Arguments.of(endpoint, " ")
                ));
    }

    @ParameterizedTest
    @MethodSource("endpointsAndKeyNames")
    @DisplayName("Запрос с апи ключом выполняется от имени владельца ключа без CSRF токена.")
    void apiKey_shouldAuthorizeKeyOwnerWithoutCsrf(OrderEndpoint endpoint, String keyName) throws Exception {
        Client client = createClient();
        String apiKey = apiKeyService.create(keyName, client);
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(endpoint.request(BASE_PATH, orderId)
                        .header("Api-Key", apiKey))
                .andExpect(status().is(endpoint.successStatus()));

        endpoint.verifyCalled(orderApiService, client.getId(), orderId);
    }

    @ParameterizedTest
    @MethodSource("endpointsAndRoles")
    @DisplayName("Запрос с сессией и валидным CSRF токеном, но без апи ключа, получает 401.")
    void session_withoutApiKey_shouldReturnUnauthorized(OrderEndpoint endpoint, Role role) throws Exception {
        UUID userId = createClient().getId();
        String csrfToken = UUID.randomUUID().toString();

        mockMvc.perform(endpoint.request(BASE_PATH, UUID.randomUUID())
                        .session(sessionOf(userId, role))
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderApiService);
    }

    @ParameterizedTest
    @MethodSource("endpointsAndInvalidKeys")
    @DisplayName("Запрос с несуществующим или пустым апи ключом получает 401.")
    void invalidApiKey_shouldReturnUnauthorized(OrderEndpoint endpoint, String apiKey) throws Exception {
        mockMvc.perform(endpoint.request(BASE_PATH, UUID.randomUUID())
                        .header("Api-Key", apiKey))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderApiService);
    }

    @ParameterizedTest
    @MethodSource("endpointsAndInvalidKeys")
    @DisplayName("Сессия клиента не заменяет невалидный апи ключ.")
    void clientSession_withInvalidApiKey_shouldReturnUnauthorized(OrderEndpoint endpoint, String apiKey)
            throws Exception {
        UUID clientId = createClient().getId();

        mockMvc.perform(endpoint.request(BASE_PATH, UUID.randomUUID())
                        .session(sessionOf(clientId, Role.CLIENT))
                        .header("Api-Key", apiKey))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderApiService);
    }

    @ParameterizedTest
    @MethodSource("endpointsAndKeyNames")
    @DisplayName("При сессии одного клиента и ключе другого запрос выполняется от имени владельца ключа.")
    void sessionOfAnotherClient_withApiKey_shouldUseKeyOwner(OrderEndpoint endpoint, String keyName)
            throws Exception {
        Client sessionClient = createClient();
        Client keyOwner = createClient();
        String apiKey = apiKeyService.create(keyName, keyOwner);
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(endpoint.request(BASE_PATH, orderId)
                        .session(sessionOf(sessionClient.getId(), Role.CLIENT))
                        .header("Api-Key", apiKey))
                .andExpect(status().is(endpoint.successStatus()));

        endpoint.verifyCalled(orderApiService, keyOwner.getId(), orderId);
        verify(orderApiService, never()).findAll(eq(sessionClient.getId()), any(), any());
        verify(orderApiService, never()).findByIdAndClientId(eq(sessionClient.getId()), any());
        verify(orderApiService, never()).createOrder(eq(sessionClient.getId()), any(), any());
        verify(orderApiService, never()).cancelOrder(eq(sessionClient.getId()), any());
    }

    @ParameterizedTest
    @EnumSource(OrderEndpoint.class)
    @DisplayName("Запрос с апи ключом не создаёт HTTP сессию.")
    void apiKey_shouldNotCreateSession(OrderEndpoint endpoint) throws Exception {
        Client client = createClient();
        String apiKey = apiKeyService.create("key", client);

        MvcResult result = mockMvc.perform(endpoint.request(BASE_PATH, UUID.randomUUID())
                        .header("Api-Key", apiKey))
                .andExpect(status().is(endpoint.successStatus()))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
    }
}
