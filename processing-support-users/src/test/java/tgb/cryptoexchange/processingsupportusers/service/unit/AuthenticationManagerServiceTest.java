package tgb.cryptoexchange.processingsupportusers.service.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import tgb.cryptoexchange.processingsupportusers.dto.AuthRequest;
import tgb.cryptoexchange.processingsupportusers.dto.TokenPair;
import tgb.cryptoexchange.processingsupportusers.dto.UserDTO;
import tgb.cryptoexchange.processingsupportusers.dto.UserRefreshTokenDTO;
import tgb.cryptoexchange.processingsupportusers.exceptions.UnauthorizedException;
import tgb.cryptoexchange.processingsupportusers.service.AuthenticationManagerService;
import tgb.cryptoexchange.processingsupportusers.service.JwtService;
import tgb.cryptoexchange.processingsupportusers.service.UserRefreshTokenService;
import tgb.cryptoexchange.processingsupportusers.service.UserService;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationManagerServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserRefreshTokenService tokenService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthenticationManagerService authService;

    @Test
    @DisplayName("Аутентификация по паролю возвращает пару токенов")
    void should_returnTokenPair_when_passwordAuthIsSuccessful() {
        AuthRequest request = new AuthRequest("user", "correct_pass", null);
        UserDTO user = UserDTO.builder().id(1L).username("user").password("encoded_pass").build();

        when(userService.getUserByUsername("user")).thenReturn(user);
        when(passwordEncoder.matches("correct_pass", "encoded_pass")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access_token");
        when(tokenService.createRefreshToken(1L)).thenReturn("refresh_token");

        TokenPair result = authService.authenticate(request);

        assertNotNull(result);
        assertEquals("access_token", result.accessToken());
        assertEquals("refresh_token", result.refreshToken());
    }

    @Test
    @DisplayName("Ошибка при неверном пароле")
    void should_throwUnauthorizedException_when_passwordIsInvalid() {
        AuthRequest request = new AuthRequest("user", "wrong_pass", null);
        UserDTO user = UserDTO.builder().username("user").password("encoded_pass").build();

        when(userService.getUserByUsername("user")).thenReturn(user);
        when(passwordEncoder.matches("wrong_pass", "encoded_pass")).thenReturn(false);

        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
                authService.authenticate(request)
        );
        assertEquals("Invalid password", exception.getMessage());
        verifyNoInteractions(jwtService, tokenService);
    }

    @Test
    @DisplayName("Обновление токенов по валидному refresh-токену")
    void should_returnTokenPair_when_refreshTokenIsValid() {
        AuthRequest request = new AuthRequest("user", null, "valid_token");
        UserRefreshTokenDTO dbToken = UserRefreshTokenDTO.builder().token("valid_token").userId(1L)
                .expiresAt(Instant.now().plusSeconds(60)).build();
        UserDTO user = UserDTO.builder().id(1L).username("user").password("encoded_pass").build();

        when(tokenService.findByToken("valid_token")).thenReturn(Optional.of(dbToken));
        when(userService.getUserById(1L)).thenReturn(user);
        when(jwtService.generateAccessToken(user)).thenReturn("new_access");
        when(tokenService.createRefreshToken(1L)).thenReturn("new_refresh");

        TokenPair result = authService.authenticate(request);

        assertNotNull(result);
        assertEquals("new_access", result.accessToken());
        assertEquals("new_refresh", result.refreshToken());
    }

    @Test
    @DisplayName("Ошибка при просроченном refresh-токене")
    void should_throwUnauthorizedException_when_refreshTokenIsExpired() {
        AuthRequest request = new AuthRequest("user", null, "expired_token");
        UserRefreshTokenDTO dbToken = UserRefreshTokenDTO.builder().token("expired_token").userId(1L)
                .expiresAt(Instant.now().minusSeconds(60)).build();

        when(tokenService.findByToken("expired_token")).thenReturn(Optional.of(dbToken));

        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
                authService.authenticate(request)
        );
        assertEquals("Invalid or expired refresh token", exception.getMessage());
        verifyNoInteractions(jwtService);
    }

}
