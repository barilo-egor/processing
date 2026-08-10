package net.rcetech.api.service;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.api.dto.ClientByApiKeyDTO;
import net.rcetech.api.enums.ClientStatus;
import net.rcetech.api.exceptions.BaseException;
import net.rcetech.clientsapi.dto.ClientResponseDTO;
import net.rcetech.clientsapi.service.ClientApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Service
@Slf4j
public class ClientAuthService {

    private final RedisTemplate<String, Object> redisTemplate;

    private final ClientApi clientApi;

    private final Long cacheTtl;

    public ClientAuthService(RedisTemplate<String, Object> redisTemplate,
            @Value("${cache.ttl.client-get-seconds}") Long cacheTtl,
            ClientApi clientApi) {
        this.redisTemplate = redisTemplate;
        this.cacheTtl = cacheTtl;
        this.clientApi = clientApi;
    }

    /**
     * Возвращает данные клиента по API-ключу, используя кэш Redis и gRPC.
     *
     * @param apiKey исходный API-ключ клиента.
     * @return {@link ClientByApiKeyDTO} с данными клиента или {@code null}, если ключ пустой или не найден.
     */
    public ClientByApiKeyDTO getClientByApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String keyHash = sha256(apiKey);
        String cacheKey = "client:" + keyHash;
        ClientByApiKeyDTO cachedClient = (ClientByApiKeyDTO) redisTemplate.opsForValue().get(cacheKey);
        if (cachedClient != null) {
            return cachedClient;
        }
        ClientResponseDTO client = clientApi.getClientByApiKey(keyHash);
        if (client == null) {
            return null;
        }
        ClientByApiKeyDTO clientByApiKeyDTO = ClientByApiKeyDTO.builder()
                .clientId(client.id())
                .username(client.username())
                .secret(client.secret())
                .apiKeyPreview(client.apiKeyPreview())
                .registeredAt(client.registeredAt())
                .status(ClientStatus.valueOf(client.status()))
                .callbackUrl(client.callbackUrl())
                .orderTimeoutSeconds(client.orderTimeoutSeconds())
                .build();
        redisTemplate.opsForValue().set(cacheKey, clientByApiKeyDTO, Duration.ofSeconds(cacheTtl));
        return clientByApiKeyDTO;
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

}
