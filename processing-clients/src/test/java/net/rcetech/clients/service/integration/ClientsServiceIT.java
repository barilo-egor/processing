package net.rcetech.clients.service.integration;

import net.rcetech.clients.entity.Client;
import net.rcetech.clients.enums.ClientStatus;
import net.rcetech.clients.exceptions.ClientAlreadyExistsException;
import net.rcetech.clients.exceptions.PasswordValidationException;
import net.rcetech.clients.service.ClientCredentialsService;
import net.rcetech.meta.clients.dto.ClientResponseDTO;
import net.rcetech.meta.clients.dto.CreateClientDTO;
import net.rcetech.meta.clients.dto.CreateClientResponseDTO;
import net.rcetech.meta.clients.dto.CreateSignatureDTO;
import net.rcetech.clients.service.ClientApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientsServiceIT extends BaseIntegrationTest {

    @Autowired
    private ClientCredentialsService clientCredentialsService;

    @Autowired
    private ClientApi clientApi;

    @Test
    @DisplayName("Создание клиента")
    void createClient_Success() {
        CreateClientDTO request = new CreateClientDTO("iron_man", "StrongPassword123!");

        CreateClientResponseDTO response = clientApi.createClient(request);

        assertThat(response.apiKey()).isNotBlank();

        Optional<Client> savedClient = clientRepository.findByUsername("iron_man");
        assertThat(savedClient).isPresent();
        assertThat(savedClient.get().getStatus()).isEqualTo(ClientStatus.ACTIVE);
        assertThat(savedClient.get().getPassword()).isNotEqualTo("StrongPassword123!");
    }

    @Test
    @DisplayName("Ошибка создания: пользователь с таким username уже существует")
    void createClient_DuplicateUsername_ThrowsAlreadyExists() {
        String username = "unique_user";
        CreateClientDTO firstRequest = new CreateClientDTO(username, "Password123!");
        CreateClientDTO secondRequest = new CreateClientDTO(username, "AnotherPass123!");

        clientApi.createClient(firstRequest);

        assertThrows(ClientAlreadyExistsException.class, () -> clientApi.createClient(secondRequest));

        long count = clientRepository.count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Валидация пароля: должен выбросить исключение валидации")
    void createClient_InvalidPassword_ReturnsDetailedError() {
        CreateClientDTO request = new CreateClientDTO("test_user", "123");

        PasswordValidationException exception = assertThrows(
                PasswordValidationException.class,
                () -> clientApi.createClient(request)
        );

        assertThat(exception.getDescription()).contains("at least 8 characters");
    }

    @Test
    @DisplayName("Создание клиента: проверка шифрования сгенерированного secret")
    void createClient_checkSecretEncryption() {
        CreateClientDTO request = new CreateClientDTO("crypto_user", "AnotherPass123!");

        CreateClientResponseDTO response = clientApi.createClient(request);

        Client savedClient = clientRepository.findByUsername("crypto_user").orElseThrow();

        assertThat(response.secret()).isNotEqualTo(savedClient.getSecret());

        String decryptedSecretFromDb = clientCredentialsService.decryptAesGcm(savedClient.getSecret());

        assertThat(response.secret()).isEqualTo(decryptedSecretFromDb);
        assertThat(response.secret()).hasSizeGreaterThan(40);
    }

    @Test
    @DisplayName("Cоздание клиента и получение по API Key")
    void createClient_getClientByApiKey() {
        CreateClientDTO createRequest = new CreateClientDTO("e2e_user", "StrongPassword123!");

        CreateClientResponseDTO createResponse = clientApi.createClient(createRequest);
        String apiKey = createResponse.apiKey();
        String secretFromCreate = createResponse.secret();

        ClientResponseDTO getResponse = clientApi.getClientByApiKey(apiKey);

        assertThat(getResponse.username()).isEqualTo("e2e_user");
        assertThat(getResponse.secret()).isEqualTo(secretFromCreate);
        assertThat(getResponse.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Получение по id")
    void success_getClientById() {
        Client client = clientRepository.save(Client.builder()
                .username("test1")
                .password("123")
                .apiKey("qwerty")
                .apiKeyPreview("qwe")
                .secret("secret")
                .status(ClientStatus.ACTIVE)
                .build());

        ClientResponseDTO getResponse = clientApi.getClientById(client.getId());

        assertThat(getResponse.username()).isEqualTo(client.getUsername());
    }

    @Test
    @DisplayName("Создание hmac256 подписи: успешный сценарий")
    void success_createSignature() {
        Client client = clientRepository.save(Client.builder()
                .username("test1")
                .password("123")
                .apiKey("qwerty")
                .apiKeyPreview("qwe")
                .secret("secret")
                .status(ClientStatus.ACTIVE)
                .build());

        String testData = "test_data";
        CreateSignatureDTO request = new CreateSignatureDTO(client.getId(), testData);
        String expectedHexSignature = "1108acad9bad25bfc7100fce7d515934b020de6d1ad51ac7be8844432afa7366";

        String signature = clientApi.createSignature(request);

        assertThat(signature)
                .isNotBlank()
                .isEqualTo(expectedHexSignature)
                .hasSize(64);
    }

}
