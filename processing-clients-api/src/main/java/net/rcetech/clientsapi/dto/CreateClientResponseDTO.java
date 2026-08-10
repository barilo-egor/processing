package net.rcetech.clientsapi.dto;

import java.time.Instant;

public record CreateClientResponseDTO(
        String username,
        String apiKey,
        String secret,
        Instant registeredAt,
        String status,
        String callbackUrl
) {

}
