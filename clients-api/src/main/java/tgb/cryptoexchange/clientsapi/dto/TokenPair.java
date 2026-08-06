package tgb.cryptoexchange.clientsapi.dto;

/**
 * Пара токенов получаемая при аутентификации
 */
public record TokenPair(String accessToken, String refreshToken) {

}
