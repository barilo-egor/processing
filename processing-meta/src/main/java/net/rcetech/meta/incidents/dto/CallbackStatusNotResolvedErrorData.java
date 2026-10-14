package net.rcetech.meta.incidents.dto;

import net.rcetech.meta.orders.OrderStatus;

import java.util.UUID;

public record CallbackStatusNotResolvedErrorData(
        UUID orderId,
        OrderStatus orderStatus,
        String merchantOrderStatus
) {}
