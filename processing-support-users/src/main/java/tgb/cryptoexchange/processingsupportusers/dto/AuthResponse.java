package tgb.cryptoexchange.processingsupportusers.dto;

/**
 * Результат аутентификации пользователя в системе.
 *
 * @param accessToken токен доступа
 */
public record AuthResponse(String accessToken) {

}
