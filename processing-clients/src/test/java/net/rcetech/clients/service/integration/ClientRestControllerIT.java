package net.rcetech.clients.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import net.rcetech.clients.dto.AuthRequest;
import net.rcetech.clients.dto.ClientDTO;
import net.rcetech.clients.entity.ClientRefreshToken;
import net.rcetech.clients.service.ClientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ClientRestControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClientService clientService;

    @Test
    @DisplayName("Создание клиента через POST /clients")
    void restCreateClient_Success() throws Exception {
        ClientDTO request = ClientDTO.builder().username("rest_user").password("StrongPass123!").build();
        mockMvc.perform(post("/api-clients/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("rest_user"))
                .andExpect(jsonPath("$.apiKey").isNotEmpty());

        assertThat(clientRepository.existsByUsername("rest_user")).isTrue();
    }

    @Test
    @DisplayName("Попытка создания дубликата должна вернуть ProblemDetail")
    void restCreateClient_Duplicate_ShouldReturnProblemDetail() throws Exception {
        String username = "rest_duplicate_user";
        clientService.create(ClientDTO.builder()
                .username(username)
                .password("StrongPass123!")
                .build());

        ClientDTO duplicateRequest = ClientDTO.builder()
                .username(username)
                .password("AnotherPass123!")
                .build();

        mockMvc.perform(post("/api-clients/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Username is already taken."))
                .andExpect(jsonPath("$.type").value("/errors/already-exists"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Попытка создания со слабым паролем должна вернуть ProblemDetail")
    void restCreateClient_InvalidPassword_ShouldReturnProblemDetail() throws Exception {
        ClientDTO request = ClientDTO.builder()
                .username("rest_user")
                .password("123!")
                .build();

        mockMvc.perform(post("/api-clients/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value(containsString("at least 8 characters")))
                .andExpect(jsonPath("$.type").value("/errors/password-validation"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Успешный вход по паролю, проверка JWT и Cookie")
    void login_ByPassword_Success() throws Exception {
        String rawPassword = "StrongPassword123!";
        ClientDTO client = clientService.create(ClientDTO.builder()
                .username("auth_test_user")
                .password(rawPassword).build());

        AuthRequest request = new AuthRequest("auth_test_user", rawPassword, null);

        var result = mockMvc.perform(post("/api-clients/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        Cookie refreshTokenCookie = result.getResponse().getCookie("refreshToken");
        assertThat(refreshTokenCookie).isNotNull();
        assertThat(refreshTokenCookie.isHttpOnly()).isTrue();
        assertThat(refreshTokenCookie.getMaxAge()).isGreaterThan(0);

        List<ClientRefreshToken> tokensInDb = tokenRepository.findAll();
        assertThat(tokensInDb).hasSize(1);
        assertThat(tokensInDb.getFirst().getClientId()).isEqualTo(client.getId());
    }

    @Test
    @DisplayName("Успешный вход по Refresh-токену")
    void login_ByRefreshToken_Success() throws Exception {
        ClientDTO client = clientService.create(ClientDTO.builder()
                .username("refresh_user").password("StrongPassword123!").build());

        UUID existingToken = UUID.randomUUID();
        tokenRepository.save(ClientRefreshToken.builder()
                .token(existingToken)
                .clientId(client.getId())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());

        AuthRequest request = new AuthRequest("refresh_user", null, existingToken.toString());

        mockMvc.perform(post("/api-clients/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        assertThat(tokenRepository.findById(existingToken)).isEmpty();
        assertThat(tokenRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Ошибка при неверном пароле")
    void login_WrongPassword_Returns401() throws Exception {
        clientService.create(ClientDTO.builder()
                .username("user").password("StrongPassword123!").build());

        AuthRequest request = new AuthRequest("user", "wrong", null);

        mockMvc.perform(post("/api-clients/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid password"))
                .andExpect(jsonPath("$.type").value("/errors/unauthorized"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401));

    }

}
