package net.rcetech.processingsupportusers.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import net.rcetech.processingsupportusers.dto.AuthRequest;
import net.rcetech.processingsupportusers.dto.TokenPair;
import net.rcetech.processingsupportusers.dto.UserDTO;
import net.rcetech.processingsupportusers.dto.UserRefreshTokenDTO;
import net.rcetech.processingsupportusers.exceptions.UnauthorizedException;

import java.time.Instant;

@Service
@Slf4j
public class AuthenticationManagerService {

    private final JwtService jwtService;

    private final UserService userService;

    private final UserRefreshTokenService tokenService;

    private final PasswordEncoder passwordEncoder;

    public AuthenticationManagerService(JwtService jwtService, UserService userService,
            UserRefreshTokenService tokenService, PasswordEncoder passwordEncoder) {
        this.jwtService = jwtService;
        this.userService = userService;
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
        UserDTO userDTO;

        if (request.password() != null) {
            log.info("Попытка аутентификации по паролю для username: '{}'", request.username());
            userDTO = userService.getUserByUsername(request.username());
            if (!passwordEncoder.matches(request.password(), userDTO.getPassword())) {
                log.warn("Authentication failed: invalid password for username: '{}'", request.username());
                throw new UnauthorizedException("Invalid password");
            }
        } else {
            String tokenPreview = request.refreshToken() != null && request.refreshToken().length() > 6
                    ? request.refreshToken().substring(0, 6) + "..."
                    : "invalid";
            log.info("Попытка аутентификации по токену: '{}'", tokenPreview);
            UserRefreshTokenDTO dbToken = tokenService.findByToken(request.refreshToken())
                    .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                    .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));
            userDTO = userService.getUserById(dbToken.getUserId());
        }
        String access = jwtService.generateAccessToken(userDTO);
        String refreshToken = tokenService.createRefreshToken(userDTO.getId());
        log.info("Успешная аутентификация пользователя'{}' (ID: {}).",
                userDTO.getUsername(), userDTO.getId());
        return new TokenPair(access, refreshToken);
    }

}
