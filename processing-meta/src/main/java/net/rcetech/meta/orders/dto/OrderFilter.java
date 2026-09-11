package net.rcetech.meta.orders.dto;

import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.serialize.MillisToInstantDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;
import java.util.UUID;

public record OrderFilter(
        UUID id,
        @JsonDeserialize(using = MillisToInstantDeserializer.class)
        Instant createdAtFrom,
        @JsonDeserialize(using = MillisToInstantDeserializer.class)
        Instant createdAtTo,
        UUID clientId,
        String internalId,
        OrderStatus status,
        String merchantOrderId
) { }
