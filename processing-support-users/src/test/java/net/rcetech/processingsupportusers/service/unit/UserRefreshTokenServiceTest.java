package net.rcetech.processingsupportusers.service.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import net.rcetech.processingsupportusers.dto.UserRefreshTokenDTO;
import net.rcetech.processingsupportusers.entity.RefreshToken;
import net.rcetech.processingsupportusers.repository.UserRefreshTokenRepository;
import net.rcetech.processingsupportusers.service.UserRefreshTokenService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRefreshTokenServiceTest {

    @Mock
    private UserRefreshTokenRepository tokenRepository;

    @InjectMocks
    private UserRefreshTokenService tokenService;

    @Test
    @DisplayName("Создание токена удаляет старые токены и сохраняет новый")
    void should_createAndSaveRefreshToken_when_userIdProvided() {
        Long userId = 42L;
        Long refreshExpiration = 86400L;
        ReflectionTestUtils.setField(tokenService, "refreshExpiration", refreshExpiration);

        when(tokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String resultToken = tokenService.createRefreshToken(userId);

        assertNotNull(resultToken);
        assertDoesNotThrow(() -> UUID.fromString(resultToken));
        verify(tokenRepository, times(1)).deleteByUserId(userId);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokenRepository, times(1)).save(captor.capture());

        RefreshToken capturedEntity = captor.getValue();
        assertEquals(userId, capturedEntity.getUserId());
        assertEquals(resultToken, capturedEntity.getToken().toString());
        assertNotNull(capturedEntity.getExpiresAt());
        assertTrue(capturedEntity.getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    @DisplayName("Поиск токена возвращает заполненный DTO, если токен найден в БД")
    void should_returnUserRefreshTokenDTO_when_tokenExists() {
        UUID tokenUuid = UUID.randomUUID();
        String tokenStr = tokenUuid.toString();
        Long userId = 42L;
        Instant expiresAt = Instant.now().plusSeconds(60);

        RefreshToken entity = new RefreshToken();
        entity.setToken(tokenUuid);
        entity.setUserId(userId);
        entity.setExpiresAt(expiresAt);

        when(tokenRepository.findById(tokenUuid)).thenReturn(Optional.of(entity));

        Optional<UserRefreshTokenDTO> result = tokenService.findByToken(tokenStr);

        assertTrue(result.isPresent());
        UserRefreshTokenDTO dto = result.get();
        assertEquals(tokenStr, dto.getToken());
        assertEquals(userId, dto.getUserId());
        assertEquals(expiresAt, dto.getExpiresAt());
    }

    @Test
    @DisplayName("Поиск токена возвращает Optional.empty(), если токена нет в БД")
    void should_returnEmptyOptional_when_tokenDoesNotExist() {
        UUID tokenUuid = UUID.randomUUID();
        String tokenStr = tokenUuid.toString();

        when(tokenRepository.findById(tokenUuid)).thenReturn(Optional.empty());

        Optional<UserRefreshTokenDTO> result = tokenService.findByToken(tokenStr);

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