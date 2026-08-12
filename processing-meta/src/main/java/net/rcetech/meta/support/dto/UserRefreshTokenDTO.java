package net.rcetech.meta.support.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class UserRefreshTokenDTO {

    private final String token;

    private final Long userId;

    private final Instant expiresAt;

}
