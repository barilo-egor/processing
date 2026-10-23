package net.rcetech.meta.incidents;

import java.util.UUID;

public record StuckSearchOrderErrorEvent(
        UUID orderId,
        String error,
        long errorTimestamp
) implements OrderIncidentEvent {

    @Override
    public IncidentType type() {
        return IncidentType.STUCK_SEARCH_ORDER_ERROR;
    }

    @Override
    public boolean onlyIfNoUnresolved() {
        return true;
    }
}
