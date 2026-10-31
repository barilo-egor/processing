package net.rcetech.clients.controller;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.service.clients.ApiKeyService;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiKeyController.class)
@Import(MetaSecurityConfig.class)
class ApiKeyControllerTest {

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @MockitoBean
    private ClientService clientService;

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource({
            "89b4e05b-c129-4ea3-a1d0-088d347b9cd2,31",
            "a2c47cba-7b49-4d9b-961c-31220f3636e4,100"
    })
    @DisplayName("Метод должен вернуть 400, если имя токена длиннее 30 символов.")
    void createApiKey_shouldReturnBadRequestIfNameTooLong(UUID clientId, int length) throws Exception {
        mockClient(clientId);

        mockMvc.perform(post("/api/client/api-key")
                        .queryParam("name", "a".repeat(length))
                        .with(csrf())
                        .with(user(clientId.toString()).roles(Role.CLIENT.name())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("Имя токена должно содержать не более 30 символов."));

        verify(apiKeyService, never()).create(any(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "89b4e05b-c129-4ea3-a1d0-088d347b9cd2,29,BdMIo7NnX6R0bCmm3cmrVDu6rBg4MXjCiR1e1P1Zpzk",
            "a2c47cba-7b49-4d9b-961c-31220f3636e4,30,q2Ow0LBRnSoHOgjRtYIaRk6yDE5l1Qq8oQwEFhN3Mds"
    })
    @DisplayName("Метод должен создать токен, если имя не длиннее 30 символов.")
    void createApiKey_shouldCreateIfNameWithinLimit(UUID clientId, int length, String key) throws Exception {
        Client client = mockClient(clientId);
        String name = "a".repeat(length);
        when(apiKeyService.create(name, client)).thenReturn(key);

        mockMvc.perform(post("/api/client/api-key")
                        .queryParam("name", name)
                        .with(csrf())
                        .with(user(clientId.toString()).roles(Role.CLIENT.name())))
                .andExpect(status().isCreated())
                .andExpect(content().string(key));
    }

    private Client mockClient(UUID clientId) {
        Client client = new Client();
        client.setId(clientId);
        when(clientService.findById(clientId)).thenReturn(Optional.of(client));
        return client;
    }
}
