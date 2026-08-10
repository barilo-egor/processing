package net.rcetech.clients.service.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import net.rcetech.clients.dto.GeneratedKeys;
import net.rcetech.clients.entity.Client;
import net.rcetech.clients.exceptions.BaseException;
import net.rcetech.clients.exceptions.FieldNotBeEmptyException;
import net.rcetech.clients.service.ClientCredentialsService;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ClientCredentialsServiceTest {

    @InjectMocks
    private ClientCredentialsService clientCredentialsService;

    @BeforeEach
    void setUp() {
        String testMasterKey = "1234567890121234";
        ReflectionTestUtils.setField(clientCredentialsService, "masterKey", testMasterKey);
    }

    @Test
    @DisplayName("Зашифрованные данные успешно расшифровываются обратно")
    void should_encryptAndDecryptSuccessfully_when_validDataProvided() {
        byte[] originalData = "secret-payload-data".getBytes(StandardCharsets.UTF_8);

        String encryptedBase64 = clientCredentialsService.encryptAesGcm(originalData);
        String resultString = clientCredentialsService.decryptAesGcm(encryptedBase64);

        assertNotNull(encryptedBase64);
        assertNotEquals(Base64.getEncoder().encodeToString(originalData), encryptedBase64);
        assertEquals("secret-payload-data", resultString);
    }

    @Test
    @DisplayName("Дешифрование падает с BaseException, если передан поврежденный шифротекст")
    void should_throwBaseException_when_encryptedTextIsCorrupted() {
        String corruptedCipher = Base64.getEncoder().encodeToString("bad-data-not-gcm-format".getBytes());

        assertThrows(BaseException.class, () ->
                clientCredentialsService.decryptAesGcm(corruptedCipher)
        );
    }

    @Test
    @DisplayName("Генерация secret корректно заполняет поля сущности Client и возвращает ключи")
    void should_populateClientFieldsAndReturnKeys_when_generatingSecret() {
        Client client = new Client();
        GeneratedKeys generatedKeys = clientCredentialsService.generateApiSecret(client);

        assertNotNull(generatedKeys);
        assertNotNull(generatedKeys.key());
        assertNotNull(generatedKeys.secret());

        String rawApiKey = generatedKeys.key();
        assertTrue(rawApiKey.startsWith("tgb_"));

        assertNotNull(client.getApiKey());
        assertEquals(64, client.getApiKey().length());

        assertNotNull(client.getApiKeyPreview());
        assertTrue(client.getApiKeyPreview().startsWith("tgb_"));
        assertTrue(client.getApiKeyPreview().contains("...."));

        assertNotNull(client.getSecret());

        String decryptedSecretFromClient = clientCredentialsService.decryptAesGcm(client.getSecret());
        assertEquals(generatedKeys.secret(), decryptedSecretFromClient);
    }

    @Test
    @DisplayName("Хэширование API-ключа возвращает корректную строку SHA-256 в hex формате")
    void should_returnCorrectSha256HexHash_when_apiKeyProvided() {
        String apiKey = "tgb_testkey_12345";
        String expectedHash = "0a2729db25cf27b5f1048bf5ef4e3b7d353b5f86b25edfdebc336a8e33a8702e";

        String resultHash = clientCredentialsService.hashSha256(apiKey);

        assertNotNull(resultHash);
        assertEquals(expectedHash, resultHash);
    }

    @Test
    @DisplayName("Успешная генерация HMAC-SHA256 подписи для валидных данных")
    void shouldGenerateCorrectHmacSha256() {
        String data = "test_data";
        String secret = "secret";
        String expectedHex = "1108acad9bad25bfc7100fce7d515934b020de6d1ad51ac7be8844432afa7366";

        String actualSignature = clientCredentialsService.generateHmacSha256(data, secret);

        assertThat(actualSignature)
                .isNotBlank()
                .hasSize(64)
                .isEqualTo(expectedHex);
    }

    @Test
    @DisplayName("Выброс IllegalArgumentException, если входные данные равны null")
    void shouldThrowExceptionWhenDataOrSecretIsNull() {
        assertThatThrownBy(() -> clientCredentialsService.generateHmacSha256(null, "secret"))
                .as("Should not be empty.")
                .isInstanceOf(FieldNotBeEmptyException.class);

        assertThatThrownBy(() -> clientCredentialsService.generateHmacSha256("data", null))
                .as("Should not be empty.")
                .isInstanceOf(FieldNotBeEmptyException.class);
    }

}