package tgb.cryptoexchange.processingsupportusers.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * @see tgb.cryptoexchange.processingsupportusers.entity.RefreshToken
 */
@Data
@Builder
public class UserRefreshTokenDTO {

    private final String token;

    private final Long userId;

    private final Instant expiresAt;

}
