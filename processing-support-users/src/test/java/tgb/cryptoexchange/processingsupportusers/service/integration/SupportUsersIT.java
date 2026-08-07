package tgb.cryptoexchange.processingsupportusers.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tgb.cryptoexchange.processingsupportusers.dto.AuthRequest;
import tgb.cryptoexchange.processingsupportusers.dto.UserDTO;
import tgb.cryptoexchange.processingsupportusers.entity.RefreshToken;
import tgb.cryptoexchange.processingsupportusers.service.UserService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SupportUsersIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Test
    @DisplayName("Создание пользователя")
    void createUser_Success() throws Exception {
        UserDTO request = UserDTO.builder().username("user").password("StrongPass123!").build();
        mockMvc.perform(post("/support-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assertThat(userRepository.existsByUsername("user")).isTrue();
    }

    @Test
    @DisplayName("Попытка создания дубликата должна вернуть ProblemDetail")
    void createUser_Duplicate_ShouldReturnProblemDetail() throws Exception {
        String username = "duplicate_user";
        userService.create(UserDTO.builder()
                .username(username)
                .password("StrongPass123!")
                .build());

        UserDTO duplicateRequest = UserDTO.builder()
                .username(username)
                .password("AnotherPass123!")
                .build();

        mockMvc.perform(post("/support-users")
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
    void createUser_InvalidPassword_ShouldReturnProblemDetail() throws Exception {
        UserDTO request = UserDTO.builder()
                .username("user")
                .password("Weak123")
                .build();

        mockMvc.perform(post("/support-users")
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
        UserDTO user = userService.create(UserDTO.builder()
                .username("auth_test_user")
                .password(rawPassword).build());

        AuthRequest request = new AuthRequest("auth_test_user", rawPassword, null);

        var result = mockMvc.perform(post("/support-users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        Cookie refreshTokenCookie = result.getResponse().getCookie("refreshToken");
        assertThat(refreshTokenCookie).isNotNull();
        assertThat(refreshTokenCookie.isHttpOnly()).isTrue();
        assertThat(refreshTokenCookie.getMaxAge()).isGreaterThan(0);

        List<RefreshToken> tokensInDb = tokenRepository.findAll();
        assertThat(tokensInDb).hasSize(1);
        assertThat(tokensInDb.getFirst().getUserId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("Успешный вход по refresh-токену")
    void login_ByRefreshToken_Success() throws Exception {
        UserDTO user = userService.create(UserDTO.builder()
                .username("refresh_user").password("StrongPassword123!").build());

        UUID existingToken = UUID.randomUUID();
        tokenRepository.save(RefreshToken.builder()
                .token(existingToken)
                .userId(user.getId())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());

        AuthRequest request = new AuthRequest("refresh_user", null, existingToken.toString());

        mockMvc.perform(post("/support-users/login")
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
        userService.create(UserDTO.builder()
                .username("user").password("StrongPassword123!").build());

        AuthRequest request = new AuthRequest("user", "wrong", null);

        mockMvc.perform(post("/support-users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid password"))
                .andExpect(jsonPath("$.type").value("/errors/unauthorized"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401));

    }

}
