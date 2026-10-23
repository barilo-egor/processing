package net.rcetech.meta.incidents;

import net.rcetech.meta.orders.OrderStatus;

import java.util.UUID;

public record ClientCallbackDeliveryUnknownIncident(
        UUID orderId,
        OrderStatus status,
        String url
) {}
