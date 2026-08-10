package net.rcetech.clientsapi.dto;

import lombok.Builder;
import lombok.Data;
import net.rcetech.clientsapi.entity.ClientRefreshToken;

import java.time.Instant;

/**
 * @see ClientRefreshToken
 */
@Data
@Builder
public class ClientRefreshTokenDTO {

    private final String token;

    private final Long clientId;

    private final Instant expiresAt;

}
