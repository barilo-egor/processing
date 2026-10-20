package net.rcetech.api.dto;

import java.util.UUID;

public record CreateOrderCommand(
        UUID clientId,
        CreateOrderRequest request,
        Integer timeoutSeconds,
        boolean testOrder
) {}
