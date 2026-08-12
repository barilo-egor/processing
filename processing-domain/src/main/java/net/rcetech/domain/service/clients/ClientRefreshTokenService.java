package net.rcetech.domain.service.clients;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import net.rcetech.meta.clients.dto.ClientRefreshTokenDTO;
import net.rcetech.domain.model.clients.ClientRefreshToken;
import net.rcetech.domain.repository.clients.ClientRefreshTokenRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@Transactional
public class ClientRefreshTokenService {

    private final ClientRefreshTokenRepository tokenRepository;

    private final Long refreshExpiration;

    private final TimeBasedEpochGenerator generator = Generators.timeBasedEpochGenerator();

    public ClientRefreshTokenService(ClientRefreshTokenRepository tokenRepository,
            @Value("${secrets.jwt.refresh-ttl-seconds}") Long refreshExpiration) {
        this.tokenRepository = tokenRepository;
        this.refreshExpiration = refreshExpiration;
    }

    /**
     * Удаляет текущий Refresh-токен клиента и генерирует для него новый Refresh-токен.
     *
     * @param clientId идентификатор клиента
     * @return строка сгенерированного Refresh-токена
     */
    public String createRefreshToken(Long clientId) {
        tokenRepository.deleteByClientId(clientId);
        ClientRefreshToken newToken = new ClientRefreshToken();
        newToken.setToken(generator.generate());
        newToken.setClientId(clientId);
        newToken.setExpiresAt(Instant.now().plusSeconds(refreshExpiration));

        return tokenRepository.save(newToken).getToken().toString();
    }

    /**
     * Находит данные Refresh-токена по его строковому представлению.
     *
     * @param token строковый идентификатор токена в формате UUID
     * @return {@link Optional} с данными токена, или пустой, если токен не найден
     */
    public Optional<ClientRefreshTokenDTO> findByToken(String token) {
        return tokenRepository.findById(UUID.fromString(token))
                .map(entity -> ClientRefreshTokenDTO.builder()
                        .token(entity.getToken().toString())
                        .clientId(entity.getClientId())
                        .expiresAt(entity.getExpiresAt())
                        .build());

    }

}
