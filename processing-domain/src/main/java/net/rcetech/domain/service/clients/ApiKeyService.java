package net.rcetech.domain.service.clients;

import net.rcetech.domain.model.clients.ApiKey;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.clients.ApiKeyRepository;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.meta.clients.dto.ApiKeyResponseDTO;
import net.rcetech.meta.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class ApiKeyService {

    public static final int MAX_API_KEYS_PER_CLIENT = 10;

    private final ApiKeyRepository apiKeyRepository;

    private final ClientRepository clientRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository, ClientRepository clientRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.clientRepository = clientRepository;
    }

    @Transactional
    public String create(String name, Client client) {
        Client lockedClient = clientRepository.findForUpdateById(client.getId())
                .orElseThrow(() -> new BadRequestException("Клиент не найден."));
        if (apiKeyRepository.existsByClientIdAndName(client.getId(), name)) {
            throw new BadRequestException("Апи ключ с именем " + name + " уже существует.");
        }
        if (apiKeyRepository.countByClientId(client.getId()) >= MAX_API_KEYS_PER_CLIENT) {
            throw new BadRequestException("Достигнут лимит в " + MAX_API_KEYS_PER_CLIENT
                    + " апи ключей. Удалите ненужный, чтобы создать новый.");
        }
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String keyHash = hashSha256(key);
        ApiKey apiKey = new ApiKey();
        apiKey.setName(name);
        apiKey.setHash(keyHash);
        apiKey.setPreview(key.substring(0, 3) + "..." + key.substring(key.length() - 3));
        apiKey.setClient(lockedClient);
        apiKeyRepository.save(apiKey);
        return key;
    }

    private String hashSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Критическая ошибка: алгоритм SHA-256 не найден", e);
        }
    }

    public List<ApiKeyResponseDTO> findAllByClientId(Client client) {
        return apiKeyRepository.findAllByClientId(client.getId());
    }

    public void delete(Client client, Long id) {
        if (!apiKeyRepository.existsByIdAndClientId(id, client.getId())) {
            throw new BadRequestException("Запись с идентификатором " + id + " не найдена.");
        }
        apiKeyRepository.deleteById(id);
    }

    public Optional<ApiKey> findByKey(String apiKey) {
        return apiKeyRepository.findByHashWithClient(hashSha256(apiKey));
    }
}
