package net.rcetech.api.config;

import net.rcetech.domain.test.SharedMySQLContainer;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PathSeparationSecurityTest {

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SharedMySQLContainer.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    private static MockHttpSession sessionOf(UUID userId, Role role) {
        MockHttpSession session = new MockHttpSession();
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                userId.toString(), null, List.of(new SimpleGrantedAuthority(role.withPrefix())));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication));
        return session;
    }

    static Stream<Arguments> supportPaths() {
        return Stream.of(
                Arguments.of(WebPath.SUPPORT_API_PATH + "/client"),
                Arguments.of(WebPath.SUPPORT_API_PATH + "/order"),
                Arguments.of(WebPath.SUPPORT_API_PATH + "/order/" + UUID.randomUUID())
        );
    }

    static Stream<Arguments> clientPathsAndSupportRoles() {
        return Stream.of(
                        WebPath.CLIENT_API_PATH + "/profile",
                        WebPath.CLIENT_API_PATH + "/api-key",
                        WebPath.CLIENT_API_PATH + "/order"
                )
                .flatMap(path -> Role.SUPPORT_USERS.stream()
                        .map(role -> Arguments.of(path, role)));
    }

    static Stream<Arguments> v1PathsAndRoles() {
        return Stream.of(
                        WebPath.V1_API_PATH + "/order",
                        WebPath.V1_API_PATH + "/profile",
                        WebPath.V1_API_PATH + "/api-key"
                )
                .flatMap(path -> Arrays.stream(Role.values())
                        .map(role -> Arguments.of(path, role)));
    }

    static Stream<Arguments> sessionApiPaths() {
        return Stream.of(
                Arguments.of(WebPath.SUPPORT_API_PATH + "/client"),
                Arguments.of(WebPath.CLIENT_API_PATH + "/profile"),
                Arguments.of(WebPath.COMMON_API_PATH + "/dictionary")
        );
    }

    @ParameterizedTest
    @MethodSource("sessionApiPaths")
    @DisplayName("Запрос к API без сессии получает 401, а не редирект на вход.")
    void noSession_onApiPath_shouldReturnUnauthorized(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @MethodSource("supportPaths")
    @DisplayName("Сессия клиента не даёт доступа к API поддержки.")
    void clientSession_onSupportPath_shouldReturnForbidden(String path) throws Exception {
        mockMvc.perform(get(path)
                        .session(sessionOf(UUID.randomUUID(), Role.CLIENT)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("clientPathsAndSupportRoles")
    @DisplayName("Сессия пользователя поддержки не даёт доступа к API клиента.")
    void supportSession_onClientPath_shouldReturnForbidden(String path, Role role) throws Exception {
        mockMvc.perform(get(path)
                        .session(sessionOf(UUID.randomUUID(), role)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("v1PathsAndRoles")
    @DisplayName("Любой путь программного API без апи ключа получает 401, даже при наличии сессии.")
    void session_onV1Path_shouldReturnUnauthorized(String path, Role role) throws Exception {
        mockMvc.perform(get(path)
                        .session(sessionOf(UUID.randomUUID(), role)))
                .andExpect(status().isUnauthorized());
    }
}
