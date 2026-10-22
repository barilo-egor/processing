package net.rcetech.meta.orders;

import java.util.UUID;

public record OrderStatusUpdatedEvent(
        UUID orderId,
        OrderStatus orderStatus,
        OrderStatusChangeSource source,
        boolean notifyClient
) {}
