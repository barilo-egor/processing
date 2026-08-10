package net.rcetech.processingsupportusers.service;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import net.rcetech.processingsupportusers.dto.UserRefreshTokenDTO;
import net.rcetech.processingsupportusers.entity.RefreshToken;
import net.rcetech.processingsupportusers.repository.UserRefreshTokenRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@Transactional
public class UserRefreshTokenService {

    private final UserRefreshTokenRepository tokenRepository;

    private final Long refreshExpiration;

    private final TimeBasedEpochGenerator generator = Generators.timeBasedEpochGenerator();

    public UserRefreshTokenService(UserRefreshTokenRepository tokenRepository,
            @Value("${secrets.jwt.refresh-ttl-seconds}") Long refreshExpiration) {
        this.tokenRepository = tokenRepository;
        this.refreshExpiration = refreshExpiration;
    }

    /**
     * Удаляет текущий Refresh-токен пользователя и генерирует для него новый Refresh-токен.
     *
     * @param userId идентификатор пользователя
     * @return строка сгенерированного Refresh-токена
     */
    public String createRefreshToken(Long userId) {
        tokenRepository.deleteByUserId(userId);
        RefreshToken newToken = new RefreshToken();
        newToken.setToken(generator.generate());
        newToken.setUserId(userId);
        newToken.setExpiresAt(Instant.now().plusSeconds(refreshExpiration));

        return tokenRepository.save(newToken).getToken().toString();
    }

    /**
     * Находит данные Refresh-токена по его строковому представлению.
     *
     * @param token строковый идентификатор токена в формате UUID
     * @return {@link Optional} с данными токена, или пустой, если токен не найден
     */
    public Optional<UserRefreshTokenDTO> findByToken(String token) {
        return tokenRepository.findById(UUID.fromString(token))
                .map(entity -> UserRefreshTokenDTO.builder()
                        .token(entity.getToken().toString())
                        .userId(entity.getUserId())
                        .expiresAt(entity.getExpiresAt())
                        .build());

    }

}
