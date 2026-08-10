package net.rcetech.api.service.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import net.rcetech.api.dto.ClientByApiKeyDTO;
import net.rcetech.api.enums.ClientStatus;
import net.rcetech.api.exceptions.BaseException;
import net.rcetech.api.service.ApiClientsGrpcService;
import net.rcetech.api.service.ClientAuthService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientAuthServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ApiClientsGrpcService apiClientsGrpcService;

    @InjectMocks
    private ClientAuthService clientAuthService;

    private String apiKey;

    private String apiKeyHash;

    private String cacheKey;

    private ClientByApiKeyDTO clientDTO;

    @BeforeEach
    void setUp() {
        apiKey = "test-api-key-123";
        apiKeyHash = sha256(apiKey);
        cacheKey = "client:" + apiKeyHash;
        clientDTO = ClientByApiKeyDTO.builder()
                .clientId(1L)
                .username("testUser")
                .apiKeyPreview("test-***")
                .secret("secret123")
                .registeredAt(Instant.now())
                .status(ClientStatus.ACTIVE)
                .callbackUrl("https://callback.url")
                .orderTimeoutSeconds(60)
                .build();

        ReflectionTestUtils.setField(clientAuthService, "cacheTtl", 300L);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new BaseException("SHA-256 algorithm not available");
        }
    }

    @Test
    void shouldReturnCachedClient_whenExistsInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(clientDTO);

        var result = clientAuthService.getClientByApiKey(apiKey);

        assertThat(result)
                .isNotNull()
                .isEqualTo(clientDTO);

        verify(redisTemplate).opsForValue();
        verify(valueOperations).get(cacheKey);
        verify(apiClientsGrpcService, never()).getClientByApiKey(anyString());
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void shouldFetchFromGrpcAndCache_whenNotInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(apiClientsGrpcService.getClientByApiKey(apiKeyHash)).thenReturn(clientDTO);
        doNothing().when(valueOperations).set(anyString(), any(), any(Duration.class));

        var result = clientAuthService.getClientByApiKey(apiKey);

        assertThat(result)
                .isNotNull()
                .isEqualTo(clientDTO);

        verify(valueOperations).get(cacheKey);
        verify(apiClientsGrpcService).getClientByApiKey(apiKeyHash);
        verify(valueOperations).set(cacheKey, clientDTO, Duration.ofSeconds(300L));
    }

    @Test
    void shouldReturnNull_whenApiKeyIsNull() {
        var result = clientAuthService.getClientByApiKey(null);

        assertThat(result).isNull();

        verify(redisTemplate, never()).opsForValue();
        verify(apiClientsGrpcService, never()).getClientByApiKey(anyString());
    }

    @Test
    void shouldReturnNull_whenApiKeyIsBlank() {
        var result = clientAuthService.getClientByApiKey("   ");

        assertThat(result).isNull();

        verify(redisTemplate, never()).opsForValue();
        verify(apiClientsGrpcService, never()).getClientByApiKey(anyString());
    }

    @Test
    void shouldReturnNull_whenApiKeyIsEmpty() {
        var result = clientAuthService.getClientByApiKey("");

        assertThat(result).isNull();

        verify(redisTemplate, never()).opsForValue();
        verify(apiClientsGrpcService, never()).getClientByApiKey(anyString());
    }

    @Test
    void shouldReturnNull_whenClientNotFoundInGrpc() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(apiClientsGrpcService.getClientByApiKey(apiKeyHash)).thenReturn(null);

        var result = clientAuthService.getClientByApiKey(apiKey);

        assertThat(result).isNull();

        verify(redisTemplate).opsForValue();
        verify(valueOperations).get(cacheKey);
        verify(apiClientsGrpcService).getClientByApiKey(apiKeyHash);
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void shouldHandleGrpcException_whenFetchingClient() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(apiClientsGrpcService.getClientByApiKey(apiKeyHash))
                .thenThrow(new BaseException("gRPC service error"));

        assertThatThrownBy(() -> clientAuthService.getClientByApiKey(apiKey))
                .isInstanceOf(BaseException.class)
                .hasMessage("gRPC service error");

        verify(redisTemplate).opsForValue();
        verify(valueOperations).get(cacheKey);
        verify(apiClientsGrpcService).getClientByApiKey(apiKeyHash);
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void shouldUseCorrectCacheKeyFormat() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(apiClientsGrpcService.getClientByApiKey(apiKeyHash)).thenReturn(clientDTO);
        doNothing().when(valueOperations).set(anyString(), any(), any(Duration.class));

        clientAuthService.getClientByApiKey(apiKey);

        var keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).get(keyCaptor.capture());
        assertThat(keyCaptor.getValue()).isEqualTo(cacheKey);

        var setKeyCaptor = ArgumentCaptor.forClass(String.class);
        var setValueCaptor = ArgumentCaptor.forClass(ClientByApiKeyDTO.class);
        var durationCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(setKeyCaptor.capture(), setValueCaptor.capture(), durationCaptor.capture());

        assertThat(setKeyCaptor.getValue()).isEqualTo(cacheKey);
        assertThat(setValueCaptor.getValue()).isEqualTo(clientDTO);
        assertThat(durationCaptor.getValue()).isEqualTo(Duration.ofSeconds(300L));
    }

    @Test
    void shouldUseSha256ForApiKeyHashing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(apiClientsGrpcService.getClientByApiKey(anyString())).thenReturn(clientDTO);
        doNothing().when(valueOperations).set(anyString(), any(), any(Duration.class));

        clientAuthService.getClientByApiKey(apiKey);

        var captor = ArgumentCaptor.forClass(String.class);
        verify(apiClientsGrpcService).getClientByApiKey(captor.capture());

        var expectedHash = sha256(apiKey);
        assertThat(captor.getValue()).isEqualTo(expectedHash);
        assertThat(captor.getValue()).hasSize(64);
    }

    @Test
    void shouldHandleDifferentApiKeysSeparately() {
        var apiKey1 = "key1";
        var apiKey2 = "key2";
        var client1 = ClientByApiKeyDTO.builder().clientId(1L).username("user1").build();
        var client2 = ClientByApiKeyDTO.builder().clientId(2L).username("user2").build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doNothing().when(valueOperations).set(anyString(), any(), any(Duration.class));

        when(valueOperations.get("client:" + sha256(apiKey1))).thenReturn(null);
        when(apiClientsGrpcService.getClientByApiKey(sha256(apiKey1))).thenReturn(client1);
        when(valueOperations.get("client:" + sha256(apiKey2))).thenReturn(client2);

        var result1 = clientAuthService.getClientByApiKey(apiKey1);
        var result2 = clientAuthService.getClientByApiKey(apiKey2);

        assertThat(result1).isEqualTo(client1);
        assertThat(result2).isEqualTo(client2);

        verify(apiClientsGrpcService, times(1)).getClientByApiKey(sha256(apiKey1));
        verify(apiClientsGrpcService, never()).getClientByApiKey(sha256(apiKey2));
    }

    @Test
    void shouldHandleRedisException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenThrow(new RuntimeException("Redis connection error"));

        assertThatThrownBy(() -> clientAuthService.getClientByApiKey(apiKey))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Redis connection error");

        verify(apiClientsGrpcService, never()).getClientByApiKey(anyString());
    }

    @Test
    void shouldNotCacheWhenClientIsNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(apiClientsGrpcService.getClientByApiKey(apiKeyHash)).thenReturn(null);

        var result = clientAuthService.getClientByApiKey(apiKey);

        assertThat(result).isNull();

        verify(valueOperations).get(cacheKey);
        verify(apiClientsGrpcService).getClientByApiKey(apiKeyHash);
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void shouldHandleApiKeyWithSpecialCharacters() {
        var specialApiKey = "api-key_with_special@chars!";
        var expectedHash = sha256(specialApiKey);
        var expectedCacheKey = "client:" + expectedHash;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedCacheKey)).thenReturn(null);
        when(apiClientsGrpcService.getClientByApiKey(expectedHash)).thenReturn(clientDTO);
        doNothing().when(valueOperations).set(anyString(), any(), any(Duration.class));

        var result = clientAuthService.getClientByApiKey(specialApiKey);

        assertThat(result).isNotNull();

        verify(valueOperations).get(expectedCacheKey);
        verify(apiClientsGrpcService).getClientByApiKey(expectedHash);
        verify(valueOperations).set(expectedCacheKey, clientDTO, Duration.ofSeconds(300L));
    }

}