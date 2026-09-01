package net.rcetech.meta.clients.dto;

import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.meta.serialize.InstantToMillisSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClientResponseDTO(
        UUID id,
        String username,
        @JsonSerialize(using = InstantToMillisSerializer.class)
        Instant registeredAt,
        ClientStatus status,
        String callbackUrl,
        Integer orderTimeoutSeconds,
        BigDecimal commissionPercent
) {}