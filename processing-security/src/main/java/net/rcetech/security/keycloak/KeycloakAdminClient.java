package net.rcetech.security.keycloak;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class KeycloakAdminClient {

    static final String REGISTRATION_ID = "keycloak-bff";

    private final ClientRegistrationRepository clientRegistrationRepository;

    private final RestClient restClient;

    public KeycloakAdminClient(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public void logout(UUID userId) {
        ClientRegistration registration = registration();
        restClient.post()
                .uri(usersUri(registration) + "/{id}/logout", userId)
                .headers(headers -> headers.setBearerAuth(accessToken(registration)))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Включает или выключает пользователя в Keycloak. Выключенный пользователь не может войти.
     *
     * @param userId  идентификатор пользователя
     * @param enabled {@code true}, чтобы включить пользователя
     */
    public void setEnabled(UUID userId, boolean enabled) {
        ClientRegistration registration = registration();
        restClient.put()
                .uri(usersUri(registration) + "/{id}", userId)
                .headers(headers -> headers.setBearerAuth(accessToken(registration)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", enabled))
                .retrieve()
                .toBodilessEntity();
    }

    private ClientRegistration registration() {
        return Objects.requireNonNull(clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID));
    }

    private String usersUri(ClientRegistration registration) {
        String issuer = Objects.requireNonNull(registration.getProviderDetails().getIssuerUri());
        return issuer.replaceFirst("/realms/", "/admin/realms/") + "/users";
    }

    private String accessToken(ClientRegistration registration) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", registration.getClientId());
        form.add("client_secret", registration.getClientSecret());
        Map<String, Object> response = restClient.post()
                .uri(registration.getProviderDetails().getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return (String) Objects.requireNonNull(response).get("access_token");
    }
}
