package net.rcetech.meta.incidents;

import net.rcetech.meta.orders.OrderStatus;

import java.util.UUID;

public record ClientCallbackUrlForbiddenEvent(
        UUID orderId,
        OrderStatus status,
        String url,
        String reason,
        long errorTimestamp
) implements OrderIncidentEvent {

    @Override
    public IncidentType type() {
        return IncidentType.CLIENT_CALLBACK_URL_FORBIDDEN;
    }

    @Override
    public boolean onlyIfNoUnresolved() {
        return true;
    }
}
