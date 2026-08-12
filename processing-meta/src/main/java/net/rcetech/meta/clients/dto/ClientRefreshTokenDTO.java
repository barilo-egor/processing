package net.rcetech.meta.clients.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ClientRefreshTokenDTO {

    private final String token;

    private final Long clientId;

    private final Instant expiresAt;

}
