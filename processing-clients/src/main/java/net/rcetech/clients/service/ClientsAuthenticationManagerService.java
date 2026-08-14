package net.rcetech.clients.service;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.service.clients.ClientRefreshTokenService;
import org.apache.commons.codec.binary.StringUtils;
import org.springframework.stereotype.Service;
import net.rcetech.meta.clients.dto.AuthRequest;
import net.rcetech.meta.clients.dto.ClientDTO;
import net.rcetech.meta.clients.dto.ClientRefreshTokenDTO;
import net.rcetech.meta.clients.dto.TokenPair;
import net.rcetech.clients.exceptions.UnauthorizedException;

import java.time.Instant;

@Service
@Slf4j
public class ClientsAuthenticationManagerService {

    private final ClientsJwtService clientsJwtService;

    private final ClientProcessService clientProcessService;

    private final ClientRefreshTokenService tokenService;

    public ClientsAuthenticationManagerService(ClientsJwtService clientsJwtService, ClientProcessService clientProcessService,
                                               ClientRefreshTokenService tokenService) {
        this.clientsJwtService = clientsJwtService;
        this.clientProcessService = clientProcessService;
        this.tokenService = tokenService;
    }

    /**
     * Аутентифицирует клиента и генерирует пару токенов (Access/Refresh).
     *
     * @param request данные аутентификации (username/password ИЛИ refreshToken)
     * @return новая пара токенов {@link TokenPair}
     * @throws UnauthorizedException если неверный пароль, токен не найден или просрочен
     */
    public TokenPair authenticate(AuthRequest request) {
        ClientDTO clientDTO;

        if (request.password() != null) {
            log.info("Попытка аутентификации по паролю для username: '{}'", request.username());
            clientDTO = clientProcessService.getClientByUsername(request.username());
            if (!StringUtils.equals(request.password(), clientDTO.getPassword())) { // TODO вернут PasswordEncoder
                log.warn("Authentication failed: invalid password for username: '{}'", request.username());
                throw new UnauthorizedException("Invalid password");
            }
        } else {
            String tokenPreview = request.refreshToken() != null && request.refreshToken().length() > 6
                    ? request.refreshToken().substring(0, 6) + "..."
                    : "invalid";
            log.info("Попытка аутентификации по токену: '{}'", tokenPreview);
            ClientRefreshTokenDTO dbToken = tokenService.findByToken(request.refreshToken())
                    .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                    .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));
            clientDTO = clientProcessService.getClientById(dbToken.getClientId());
        }
        String access = clientsJwtService.generateAccessToken(clientDTO);
        String refreshToken = tokenService.createRefreshToken(clientDTO.getId());
        log.info("Успешная аутентификация пользователя'{}' (ID: {}).",
                clientDTO.getUsername(), clientDTO.getId());
        return new TokenPair(access, refreshToken);
    }

}
