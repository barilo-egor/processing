package net.rcetech.clientsapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import net.rcetech.clientsapi.dto.AuthRequest;
import net.rcetech.clientsapi.dto.ClientDTO;
import net.rcetech.clientsapi.dto.ClientRefreshTokenDTO;
import net.rcetech.clientsapi.dto.TokenPair;
import net.rcetech.clientsapi.exceptions.UnauthorizedException;

import java.time.Instant;

@Service
@Slf4j
public class AuthenticationManagerService {

    private final JwtService jwtService;

    private final ClientService clientService;

    private final ClientRefreshTokenService tokenService;

    private final PasswordEncoder passwordEncoder;

    public AuthenticationManagerService(JwtService jwtService, ClientService clientService,
            ClientRefreshTokenService tokenService, PasswordEncoder passwordEncoder) {
        this.jwtService = jwtService;
        this.clientService = clientService;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
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
            clientDTO = clientService.getClientByUsername(request.username());
            if (!passwordEncoder.matches(request.password(), clientDTO.getPassword())) {
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
            clientDTO = clientService.getClientById(dbToken.getClientId());
        }
        String access = jwtService.generateAccessToken(clientDTO);
        String refreshToken = tokenService.createRefreshToken(clientDTO.getId());
        log.info("Успешная аутентификация пользователя'{}' (ID: {}).",
                clientDTO.getUsername(), clientDTO.getId());
        return new TokenPair(access, refreshToken);
    }

}
