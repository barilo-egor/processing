package net.rcetech.meta.orders.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderResponseDTO(
        UUID id,
        Long clientId,
        String internalId,
        String status,
        Integer amount,
        boolean enableUniqueAmount,
        String callbackUrl,
        Instant createdAt
) {

}
