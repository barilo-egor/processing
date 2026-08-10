package net.rcetech.meta.clients.dto;

import java.time.Instant;

public record ClientResponseDTO(
        Long id,
        String username,
        String secret,
        String apiKeyPreview,
        Instant registeredAt,
        String status,
        String callbackUrl,
        Integer orderTimeoutSeconds
) {

}
