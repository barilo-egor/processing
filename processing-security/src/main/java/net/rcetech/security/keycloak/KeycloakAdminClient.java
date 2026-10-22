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
        ClientRegistration registration = Objects.requireNonNull(
                clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID));
        String issuer = Objects.requireNonNull(registration.getProviderDetails().getIssuerUri());
        restClient.post()
                .uri(issuer.replaceFirst("/realms/", "/admin/realms/") + "/users/{id}/logout", userId)
                .headers(headers -> headers.setBearerAuth(accessToken(registration)))
                .retrieve()
                .toBodilessEntity();
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
