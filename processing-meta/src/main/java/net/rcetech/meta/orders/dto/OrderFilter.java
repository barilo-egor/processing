package net.rcetech.meta.orders.dto;

import net.rcetech.meta.orders.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderFilter(
        UUID id,
        Instant createdAtFrom,
        Instant createdAtTo,
        UUID clientId,
        String internalId,
        OrderStatus status,
        String merchantOrderId
) { }
