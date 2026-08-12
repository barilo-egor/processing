package net.rcetech.clients.service;

import net.rcetech.clients.exceptions.*;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.clients.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import net.rcetech.meta.clients.dto.ClientDTO;
import net.rcetech.meta.clients.dto.GeneratedKeys;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.domain.mapper.client.ClientMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientProcessServiceTest {

    @Mock
    private ClientService clientService;

    @Mock
    private ClientCredentialsService clientCredentialsService;

    @Mock
    private ClientMapper clientMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ClientProcessService clientProcessService;

    @Test
    @DisplayName("Создание клиента при валидных данных")
    void should_createClient_when_dataIsValid() {
        ClientDTO inputDto = ClientDTO.builder().username("new_user").password("Valid123!").build();
        Client savedClient = Client.builder().id(1L).username("new_user").password("encoded_pass").build();
        GeneratedKeys generatedKeys = new GeneratedKeys("apiKey", "secret");
        ClientDTO expectedDto = ClientDTO.builder().username("new_user").password(null).build();

        when(clientService.existsByUsername("new_user")).thenReturn(false);
        when(passwordEncoder.encode("Valid123!")).thenReturn("encoded_pass");
        when(clientService.save(any(Client.class))).thenReturn(savedClient);
        when(clientCredentialsService.generateApiSecret(any(Client.class))).thenReturn(generatedKeys);
        when(clientMapper.createdClientToDTO(savedClient, generatedKeys)).thenReturn(expectedDto);

        ClientDTO result = clientProcessService.create(inputDto);

        assertNotNull(result);
        assertEquals("new_user", result.getUsername());

        ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);
        verify(clientService).save(clientCaptor.capture());
        Client capturedClient = clientCaptor.getValue();
        assertEquals(ClientStatus.ACTIVE, capturedClient.getStatus());
        assertEquals("encoded_pass", capturedClient.getPassword());
    }

    @Test
    @DisplayName("Создание клиента падает с исключением, если имя пользователя уже занято")
    void should_throwClientAlreadyExistsException_when_usernameIsTaken() {
        ClientDTO inputDto = ClientDTO.builder().username("existing_user").password("Valid123!").build();
        when(clientService.existsByUsername("existing_user")).thenReturn(true);

        assertThrows(ClientAlreadyExistsException.class, () -> clientProcessService.create(inputDto));
        verify(clientService, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short",
            "NoSpecial123",
            "NoDigit!!!",
            "lowercase123!",
            "UPPERCASE123!"
    })
    @DisplayName("Создание клиента падает с исключением при невалидном формате пароля")
    void should_throwPasswordValidationException_when_passwordDoesNotMatchRegex(String invalidPassword) {
        ClientDTO inputDto = ClientDTO.builder().username("user").password(invalidPassword).build();
        when(clientService.existsByUsername("user")).thenReturn(false);

        assertThrows(PasswordValidationException.class, () -> clientProcessService.create(inputDto));
        verifyNoInteractions(passwordEncoder, clientCredentialsService);
    }

    @Test
    @DisplayName("Получение клиента по валидному API-ключу")
    void should_returnClientByApiKey_when_apiKeyIsValid() {
        String rawApiKey = "raw_key";
        String hashedKey = "hashed_key";
        Client client = Client.builder().id(1L).apiKey(hashedKey).secret("encrypted_secret").build();
        ClientDTO expectedDto = ClientDTO.builder().build();

        when(clientCredentialsService.hashSha256(rawApiKey)).thenReturn(hashedKey);
        when(clientService.findByApiKey(hashedKey)).thenReturn(Optional.of(client));
        when(clientCredentialsService.decryptAesGcm("encrypted_secret")).thenReturn("decrypted_secret");
        when(clientMapper.getClientByApiKeyDTO(client, "decrypted_secret")).thenReturn(expectedDto);

        ClientDTO result = clientProcessService.getClientByApiKey(rawApiKey);

        assertNotNull(result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    @DisplayName("Получение клиента по API-ключу падает, если ключ пустой или равен null")
    void should_throwInvalidApiKeyException_when_apiKeyIsNullOrEmpty(String invalidKey) {
        assertThrows(InvalidApiKeyException.class, () -> clientProcessService.getClientByApiKey(invalidKey));
        assertThrows(InvalidApiKeyException.class, () -> clientProcessService.getClientByApiKey(null));

        verifyNoInteractions(clientService);
    }

    @Test
    @DisplayName("Получение клиента по API-ключу падает, если ключ не найден в репозитории")
    void should_throwInvalidUserNotFoundException_when_clientNotFoundByApiKey() {
        String rawApiKey = "unknown_key";
        String hashedKey = "hashed_unknown_key";
        when(clientCredentialsService.hashSha256(rawApiKey)).thenReturn(hashedKey);
        when(clientService.findByApiKey(hashedKey)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> clientProcessService.getClientByApiKey(rawApiKey));
    }

    @Test
    @DisplayName("Получение клиента по имени пользователя")
    void should_returnClientDto_when_usernameExists() {
        String username = "john_doe";
        Client client = Client.builder().username(username).build();
        ClientDTO expectedDto = ClientDTO.builder().username(username).password(null).build();

        when(clientService.findByUsername(username)).thenReturn(Optional.of(client));
        when(clientMapper.clientToDTO(client)).thenReturn(expectedDto);

        ClientDTO result = clientProcessService.getClientByUsername(username);

        assertNotNull(result);
        assertEquals(username, result.getUsername());
    }

    @Test
    @DisplayName("Получение клиента по имени пользователя падает, если он не найден")
    void should_throwNotFoundException_when_usernameDoesNotExist() {
        String username = "missing_user";
        when(clientService.findByUsername(username)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> clientProcessService.getClientByUsername(username));
    }

}