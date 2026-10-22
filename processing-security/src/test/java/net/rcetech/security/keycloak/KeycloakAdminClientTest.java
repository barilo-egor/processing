package net.rcetech.security.keycloak;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClientException;
import org.wiremock.spring.EnableWireMock;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = KeycloakAdminClient.class)
@EnableWireMock
class KeycloakAdminClientTest {

    private static final String REALM_PATH = "/realms/processing";

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private KeycloakAdminClient keycloakAdminClient;

    @Value("${wiremock.server.baseUrl}")
    private String wireMockUrl;

    @CsvSource({
            "6f1d2c3b-4a5e-4f60-8b7c-9d0e1f2a3b4c,keycloak-bff,secret-1,token-1",
            "0a9b8c7d-6e5f-4a3b-9c1d-2e3f4a5b6c7d,bff,s3cr3t,eyJhbGciOi"
    })
    @ParameterizedTest
    @DisplayName("Метод должен получить токен по client_credentials и завершить сессии пользователя в Keycloak.")
    void logout_shouldLogoutUserWithServiceAccountToken(UUID userId, String clientId, String clientSecret,
                                                        String accessToken) {
        mockRegistration(clientId, clientSecret);
        stubToken(accessToken);
        stubFor(post("/admin" + REALM_PATH + "/users/" + userId + "/logout").willReturn(noContent()));

        keycloakAdminClient.logout(userId);

        verify(postRequestedFor(urlEqualTo(REALM_PATH + "/protocol/openid-connect/token"))
                .withFormParam("grant_type", equalTo("client_credentials"))
                .withFormParam("client_id", equalTo(clientId))
                .withFormParam("client_secret", equalTo(clientSecret)));
        verify(postRequestedFor(urlEqualTo("/admin" + REALM_PATH + "/users/" + userId + "/logout"))
                .withHeader("Authorization", equalTo("Bearer " + accessToken)));
    }

    @ValueSource(ints = {403, 500})
    @ParameterizedTest
    @DisplayName("Метод должен выбросить исключение, если Keycloak не завершил сессии.")
    void logout_shouldThrowIfLogoutFailed(int status) {
        UUID userId = UUID.randomUUID();
        mockRegistration("keycloak-bff", "secret");
        stubToken("token");
        stubFor(post("/admin" + REALM_PATH + "/users/" + userId + "/logout").willReturn(status(status)));

        assertThrows(RestClientException.class, () -> keycloakAdminClient.logout(userId));
    }

    @ValueSource(ints = {401, 503})
    @ParameterizedTest
    @DisplayName("Метод должен выбросить исключение и не вызывать logout, если токен не получен.")
    void logout_shouldThrowIfTokenNotReceived(int status) {
        UUID userId = UUID.randomUUID();
        mockRegistration("keycloak-bff", "secret");
        stubFor(post(REALM_PATH + "/protocol/openid-connect/token").willReturn(status(status)));

        assertThrows(RestClientException.class, () -> keycloakAdminClient.logout(userId));
        verify(0, postRequestedFor(urlPathMatching("/admin/.*")));
    }

    private void mockRegistration(String clientId, String clientSecret) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(KeycloakAdminClient.REGISTRATION_ID)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri(wireMockUrl + REALM_PATH + "/protocol/openid-connect/auth")
                .tokenUri(wireMockUrl + REALM_PATH + "/protocol/openid-connect/token")
                .issuerUri(wireMockUrl + REALM_PATH)
                .build();
        when(clientRegistrationRepository.findByRegistrationId(KeycloakAdminClient.REGISTRATION_ID))
                .thenReturn(registration);
    }

    private void stubToken(String accessToken) {
        stubFor(post(REALM_PATH + "/protocol/openid-connect/token")
                .willReturn(okJson("{\"access_token\":\"" + accessToken + "\",\"token_type\":\"Bearer\"}")));
    }
}
