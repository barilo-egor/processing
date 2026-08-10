package net.rcetech.clientsapi.service.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import net.rcetech.clientsapi.dto.AuthRequest;
import net.rcetech.clientsapi.dto.ClientDTO;
import net.rcetech.clientsapi.dto.ClientRefreshTokenDTO;
import net.rcetech.clientsapi.dto.TokenPair;
import net.rcetech.clientsapi.exceptions.UnauthorizedException;
import net.rcetech.clientsapi.service.AuthenticationManagerService;
import net.rcetech.clientsapi.service.ClientRefreshTokenService;
import net.rcetech.clientsapi.service.ClientService;
import net.rcetech.clientsapi.service.JwtService;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationManagerServiceTest {

    @Mock
    private ClientService clientService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ClientRefreshTokenService tokenService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthenticationManagerService authService;

    @Test
    @DisplayName("Аутентификация по паролю возвращает пару токенов")
    void should_returnTokenPair_when_passwordAuthIsSuccessful() {
        AuthRequest request = new AuthRequest("user", "correct_pass", null);
        ClientDTO client = ClientDTO.builder().id(1L).username("user").password("encoded_pass").build();

        when(clientService.getClientByUsername("user")).thenReturn(client);
        when(passwordEncoder.matches("correct_pass", "encoded_pass")).thenReturn(true);
        when(jwtService.generateAccessToken(client)).thenReturn("access_token");
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
        ClientDTO client = ClientDTO.builder().username("user").password("encoded_pass").build();

        when(clientService.getClientByUsername("user")).thenReturn(client);
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
        ClientRefreshTokenDTO dbToken = ClientRefreshTokenDTO.builder().token("valid_token").clientId(1L)
                .expiresAt(Instant.now().plusSeconds(60)).build();
        ClientDTO client = ClientDTO.builder().id(1L).username("user").password("encoded_pass").build();

        when(tokenService.findByToken("valid_token")).thenReturn(Optional.of(dbToken));
        when(clientService.getClientById(1L)).thenReturn(client);
        when(jwtService.generateAccessToken(client)).thenReturn("new_access");
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
        ClientRefreshTokenDTO dbToken = ClientRefreshTokenDTO.builder().token("expired_token").clientId(1L)
                .expiresAt(Instant.now().minusSeconds(60)).build();

        when(tokenService.findByToken("expired_token")).thenReturn(Optional.of(dbToken));

        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
                authService.authenticate(request)
        );
        assertEquals("Invalid or expired refresh token", exception.getMessage());
        verifyNoInteractions(jwtService);
    }

}
