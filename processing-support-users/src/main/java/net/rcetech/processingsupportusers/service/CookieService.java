package net.rcetech.processingsupportusers.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@Slf4j
public class CookieService {

    private final Environment env;

    private final Long refreshExpiration;

    public CookieService(Environment env, @Value("${secrets.jwt.refresh-ttl-seconds}") Long refreshExpiration) {
        this.env = env;
        this.refreshExpiration = refreshExpiration;
    }

    /**
     * Создает защищенную HTTP-only авторизационную cookie для Refresh-токена.
     *
     * @param refreshToken строка токена для сохранения в cookie
     * @return настроенный объект {@link ResponseCookie}
     */
    public ResponseCookie createRefreshTokenCookie(String refreshToken) {
        boolean isDev = Arrays.asList(env.getActiveProfiles()).contains("dev-web-app");

        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(!isDev)
                .path("/")
                .maxAge(refreshExpiration)
                .sameSite(isDev ? "Lax" : "Strict")
                .build();
    }

}
