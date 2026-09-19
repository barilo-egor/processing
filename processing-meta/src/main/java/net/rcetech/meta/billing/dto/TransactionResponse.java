package net.rcetech.meta.billing.dto;

import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.billing.TransactionType;
import net.rcetech.meta.serialize.InstantToMillisSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID clientId,
        String clientUsername,
        @JsonSerialize(using = InstantToMillisSerializer.class)
        Instant createdAt,
        Operation operation,
        Integer amount,
        TransactionType type,
        String comment
) {}
