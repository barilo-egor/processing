package net.rcetech.processingdetailsapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import net.rcetech.processingdetailsapi.dto.ClientByApiKeyDTO;
import net.rcetech.processingdetailsapi.exceptions.BaseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Service
@Slf4j
public class ClientAuthService {

    private final RedisTemplate<String, Object> redisTemplate;

    private final ApiClientsGrpcService apiClientsGrpcService;

    private final Long cacheTtl;

    public ClientAuthService(RedisTemplate<String, Object> redisTemplate,
            @Value("${cache.ttl.client-get-seconds}") Long cacheTtl,
            ApiClientsGrpcService apiClientsGrpcService) {
        this.redisTemplate = redisTemplate;
        this.cacheTtl = cacheTtl;
        this.apiClientsGrpcService = apiClientsGrpcService;
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
        ClientByApiKeyDTO client = apiClientsGrpcService.getClientByApiKey(keyHash);
        if (client == null) {
            return null;
        }
        redisTemplate.opsForValue().set(cacheKey, client, Duration.ofSeconds(cacheTtl));
        return client;
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
