package net.rcetech.meta.orders.dto;

import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.serialize.InstantToMillisSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

import java.time.Instant;
import java.util.UUID;

public record OrderSummary (
        UUID id,
        @JsonSerialize(using = InstantToMillisSerializer.class)
        Instant createdAt,
        String clientUsername,
        String internalId,
        OrderStatus status,
        Integer amount
) {}
