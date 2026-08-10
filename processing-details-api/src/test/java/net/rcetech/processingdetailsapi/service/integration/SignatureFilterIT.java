package net.rcetech.processingdetailsapi.service.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import net.rcetech.processingdetailsapi.dto.ClientByApiKeyDTO;
import net.rcetech.processingdetailsapi.enums.ClientStatus;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignatureFilterIT extends BaseIntegrationTest {

    @BeforeEach
    void setUpAuth() {
        ClientByApiKeyDTO mockClient = ClientByApiKeyDTO.builder()
                .clientId(99L)
                .username("Professional Merchant")
                .apiKeyPreview("prof...")
                .secret(CLIENT_SECRET)
                .status(ClientStatus.ACTIVE)
                .orderTimeoutSeconds(60)
                .build();

        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOperationsMock = mock(ValueOperations.class);
        when(valueOperationsMock.get(anyString()))
                .thenReturn(mockClient);

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperationsMock);
        when(valueOperationsMock.get(anyString())).thenReturn(mockClient);
    }

    @Test
    void shouldReturn401Unauthorized_WhenAuthorizationHeaderIsMissing() {
        webTestClient.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()

                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Unauthorized")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.detail").isEqualTo("Unauthorized");
    }

    @Test
    void shouldReturn401Unauthorized_WhenSignatureOrTimestampHeaderIsMissing() {
        webTestClient.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Api-Key " + API_KEY)
                .bodyValue("{}")
                .exchange()

                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Invalid signature")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.detail").isEqualTo("The provided signature does not match.");
    }

    @Test
    void shouldReturn403Forbidden_WhenClientIsBlocked() throws Exception {
        ClientByApiKeyDTO blockedClient = ClientByApiKeyDTO.builder()
                .clientId(99L)
                .username("Blocked Merchant")
                .secret(CLIENT_SECRET)
                .status(ClientStatus.BLOCKED)
                .build();

        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valOps = Mockito.mock(ValueOperations.class);
        Mockito.when(valOps.get(anyString())).thenReturn(blockedClient);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valOps);

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String dataToSign = String.format("POST|/api/v1/orders|%s|{}", timestamp);
        String signature = calculateHmacSha256(dataToSign);

        webTestClient.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Api-Key " + API_KEY)
                .header("Signature", signature)
                .header("X-Timestamp", timestamp)
                .bodyValue("{}")
                .exchange()

                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Forbidden")
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.detail").isEqualTo("User blocked.");
    }

    @Test
    void shouldReturn401Unauthorized_WhenTimestampIsExpired() throws Exception {
        Instant currentSystemTime = Instant.now();
        String expiredTimestamp = String.valueOf(currentSystemTime.getEpochSecond() - 600);

        String dataToSign = String.format("POST|/api/v1/orders|%s|{}", expiredTimestamp);
        String signature = calculateHmacSha256(dataToSign);

        try (MockedStatic<Instant> mockedInstant = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            mockedInstant.when(Instant::now).thenReturn(currentSystemTime);

            webTestClient.post()
                    .uri("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Api-Key " + API_KEY)
                    .header("Signature", signature)
                    .header("X-Timestamp", expiredTimestamp)
                    .bodyValue("{}")
                    .exchange()

                    .expectStatus().isUnauthorized()
                    .expectBody()
                    .jsonPath("$.title").isEqualTo("Invalid signature");
        }
    }

}
