package net.rcetech.support.dto;

import lombok.Builder;
import lombok.Data;
import net.rcetech.support.entity.RefreshToken;

import java.time.Instant;

/**
 * @see RefreshToken
 */
@Data
@Builder
public class UserRefreshTokenDTO {

    private final String token;

    private final Long userId;

    private final Instant expiresAt;

}
