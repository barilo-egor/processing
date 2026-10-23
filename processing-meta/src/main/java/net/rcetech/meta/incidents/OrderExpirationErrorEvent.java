package net.rcetech.meta.incidents;

import java.util.UUID;

public record OrderExpirationErrorEvent(
        UUID orderId,
        String error,
        long errorTimestamp
) implements OrderIncidentEvent {

    @Override
    public IncidentType type() {
        return IncidentType.ORDER_EXPIRATION_ERROR;
    }

    @Override
    public boolean onlyIfNoUnresolved() {
        return true;
    }
}
