package tgb.cryptoexchange.clientsapi.dto;

/**
 * Данные запроса для аутентификации пользователя в системе.
 *
 * @param username     имя пользователя, обязательное поле для обоих сценариев
 * @param password     пароль пользователя; обязателен, если не передан {@code refreshToken}
 * @param refreshToken токен обновления сессии; обязателен, если не передан {@code password}
 */
public record AuthRequest(String username, String password, String refreshToken) {

}
