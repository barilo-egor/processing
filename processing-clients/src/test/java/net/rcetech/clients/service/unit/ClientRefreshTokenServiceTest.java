package net.rcetech.clients.service.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import net.rcetech.clients.dto.ClientRefreshTokenDTO;
import net.rcetech.clients.entity.ClientRefreshToken;
import net.rcetech.clients.repository.ClientRefreshTokenRepository;
import net.rcetech.clients.service.ClientRefreshTokenService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientRefreshTokenServiceTest {

    @Mock
    private ClientRefreshTokenRepository tokenRepository;

    @InjectMocks
    private ClientRefreshTokenService tokenService;

    @Test
    @DisplayName("Создание токена удаляет старые токены и сохраняет новый")
    void should_createAndSaveRefreshToken_when_clientIdProvided() {
        Long clientId = 42L;
        Long refreshExpiration = 86400L;
        ReflectionTestUtils.setField(tokenService, "refreshExpiration", refreshExpiration);

        when(tokenRepository.save(any(ClientRefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String resultToken = tokenService.createRefreshToken(clientId);

        assertNotNull(resultToken);
        assertDoesNotThrow(() -> UUID.fromString(resultToken));
        verify(tokenRepository, times(1)).deleteByClientId(clientId);

        ArgumentCaptor<ClientRefreshToken> captor = ArgumentCaptor.forClass(ClientRefreshToken.class);
        verify(tokenRepository, times(1)).save(captor.capture());

        ClientRefreshToken capturedEntity = captor.getValue();
        assertEquals(clientId, capturedEntity.getClientId());
        assertEquals(resultToken, capturedEntity.getToken().toString());
        assertNotNull(capturedEntity.getExpiresAt());
        assertTrue(capturedEntity.getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    @DisplayName("Поиск токена возвращает заполненный DTO, если токен найден в БД")
    void should_returnClientRefreshTokenDTO_when_tokenExists() {
        UUID tokenUuid = UUID.randomUUID();
        String tokenStr = tokenUuid.toString();
        Long clientId = 42L;
        Instant expiresAt = Instant.now().plusSeconds(60);

        ClientRefreshToken entity = new ClientRefreshToken();
        entity.setToken(tokenUuid);
        entity.setClientId(clientId);
        entity.setExpiresAt(expiresAt);

        when(tokenRepository.findById(tokenUuid)).thenReturn(Optional.of(entity));

        Optional<ClientRefreshTokenDTO> result = tokenService.findByToken(tokenStr);

        assertTrue(result.isPresent());
        ClientRefreshTokenDTO dto = result.get();
        assertEquals(tokenStr, dto.getToken());
        assertEquals(clientId, dto.getClientId());
        assertEquals(expiresAt, dto.getExpiresAt());
    }

    @Test
    @DisplayName("Поиск токена возвращает Optional.empty(), если токена нет в БД")
    void should_returnEmptyOptional_when_tokenDoesNotExist() {
        UUID tokenUuid = UUID.randomUUID();
        String tokenStr = tokenUuid.toString();

        when(tokenRepository.findById(tokenUuid)).thenReturn(Optional.empty());

        Optional<ClientRefreshTokenDTO> result = tokenService.findByToken(tokenStr);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Поиск токена бросает IllegalArgumentException, если передан невалидный формат UUID")
    void should_throwIllegalArgumentException_when_tokenFormatIsInvalid() {
        String invalidTokenStr = "not-a-valid-uuid";

        assertThrows(IllegalArgumentException.class, () ->
                tokenService.findByToken(invalidTokenStr)
        );
        verifyNoInteractions(tokenRepository);
    }

}