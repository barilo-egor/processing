package net.rcetech.clients.service.integration;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.BadRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import net.rcetech.clients.entity.Client;
import net.rcetech.clients.enums.ClientStatus;
import net.rcetech.clients.service.ClientCredentialsService;
import tgb.cryptoexchange.grpc.generated.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

class ClientsServiceIT extends BaseIntegrationTest {

    @Autowired
    private ClientCredentialsService clientCredentialsService;

    private ClientsServiceGrpc.ClientsServiceBlockingStub blockingStub;

    @BeforeEach
    void setup() {
        blockingStub = ClientsServiceGrpc.newBlockingStub(channel);
    }

    @Test
    @DisplayName("Создание клиента")
    void createClient_Success() {
        CreateClientGrpc request = CreateClientGrpc.newBuilder()
                .setUsername("iron_man")
                .setPassword("StrongPassword123!")
                .build();

        CreateClientResponseGrpc response = blockingStub.createClient(request);

        assertNotNull(response.getApiKey());

        Optional<Client> savedClient = clientRepository.findByUsername("iron_man");
        assertTrue(savedClient.isPresent());
        assertEquals(ClientStatus.ACTIVE, savedClient.get().getStatus());
        assertNotEquals("StrongPassword123!", savedClient.get().getPassword());
    }

    @Test
    @DisplayName("Ошибка создания: пользователь с таким username уже существует")
    void createClient_DuplicateUsername_ThrowsAlreadyExists() {
        String username = "unique_user";
        var firstRequest = CreateClientGrpc.newBuilder()
                .setUsername(username)
                .setPassword("Password123!")
                .build();

        blockingStub.createClient(firstRequest);

        var secondRequest = CreateClientGrpc.newBuilder()
                .setUsername(username)
                .setPassword("AnotherPass123!")
                .build();

        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            blockingStub.createClient(secondRequest);
        });

        assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);

        com.google.rpc.Status status = StatusProto.fromThrowable(exception);
        BadRequest badRequest = null;
        try {
            badRequest = status.getDetails(0).unpack(BadRequest.class);
        } catch (InvalidProtocolBufferException e) {
            fail("Не удалось распаковать детали ошибки");
        }

        assertThat(badRequest.getFieldViolations(0).getField()).isEqualTo("username");
        assertThat(badRequest.getFieldViolations(0).getDescription()).isEqualTo("Username is already taken.");

        long count = clientRepository.count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Валидация пароля: должен вернуть структурированную ошибку INVALID_ARGUMENT")
    void createClient_InvalidPassword_ReturnsDetailedError() {
        var request = CreateClientGrpc.newBuilder()
                .setUsername("test_user")
                .setPassword("123")
                .build();

        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            blockingStub.createClient(request);
        });

        assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);

        com.google.rpc.Status status = StatusProto.fromThrowable(exception);
        BadRequest badRequest = null;

        for (com.google.protobuf.Any any : status.getDetailsList()) {
            if (any.is(BadRequest.class)) {
                try {
                    badRequest = any.unpack(BadRequest.class);
                } catch (InvalidProtocolBufferException e) {
                    fail("Не удалось распаковать BadRequest");
                }
            }
        }

        assertThat(badRequest).isNotNull();
        assertThat(badRequest.getFieldViolations(0).getField()).isEqualTo("password");
        assertThat(badRequest.getFieldViolations(0).getDescription()).contains("at least 8 characters");
    }

    @Test
    @DisplayName("Создание клиента: проверка шифрования сгенерированного secret")
    void createClient_checkSecretEncryption() {
        var request = CreateClientGrpc.newBuilder()
                .setUsername("crypto_user")
                .setPassword("AnotherPass123!")
                .build();

        var response = blockingStub.createClient(request);

        var savedClient = clientRepository.findByUsername("crypto_user")
                .orElseThrow();

        assertThat(response.getSecret()).isNotEqualTo(savedClient.getSecret());

        String decryptedSecretFromDb = clientCredentialsService.decryptAesGcm(savedClient.getSecret());

        assertThat(response.getSecret()).isEqualTo(decryptedSecretFromDb);
        assertThat(response.getSecret()).hasSizeGreaterThan(40);
    }

    @Test
    @DisplayName("Cоздание клиента и получение по API Key")
    void createClient_getClientByApiKey() {
        var createRequest = CreateClientGrpc.newBuilder()
                .setUsername("e2e_user")
                .setPassword("StrongPassword123!")
                .build();

        var createResponse = blockingStub.createClient(createRequest);
        String apiKey = createResponse.getApiKey();
        String secretFromCreate = createResponse.getSecret();

        var getRequest = GetClientByApiKeyGrpc.newBuilder()
                .setApiKey(apiKey)
                .build();

        var getResponse = blockingStub.getClientByApiKey(getRequest);

        assertThat(getResponse.getUsername()).isEqualTo("e2e_user");
        assertThat(getResponse.getSecret()).isEqualTo(secretFromCreate);
        assertThat(getResponse.getStatus()).isEqualTo("ACTIVE");
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

        var getRequest = GetClientByIdGrpc.newBuilder()
                .setId(client.getId())
                .build();

        var getResponse = blockingStub.getClientById(getRequest);
        assertThat(getResponse.getUsername()).isEqualTo(client.getUsername());
    }

    @Test
    @DisplayName("Создание hmac256 подписи: успешный сценарий через gRPC")
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
        var getRequest = CreateSignatureGrpc.newBuilder()
                .setClientId(client.getId())
                .setData(testData)
                .build();

        String expectedHexSignature = "1108acad9bad25bfc7100fce7d515934b020de6d1ad51ac7be8844432afa7366";

        var getResponse = blockingStub.createSignature(getRequest);

        assertThat(getResponse).isNotNull();
        assertThat(getResponse.getSignature())
                .isNotBlank()
                .isEqualTo(expectedHexSignature);
        assertThat(getResponse.getSignature()).hasSize(64);
    }

}
