package net.rcetech.meta.incidents;

import java.util.UUID;

public record AsyncDetailsSearchErrorEvent(
        UUID orderId,
        String error,
        long errorTimestamp
) implements IncidentEvent {

    @Override
    public IncidentType type() {
        return IncidentType.ASYNC_DETAILS_SEARCH_ERROR;
    }

    @Override
    public boolean onlyIfNoUnresolved() {
        return true;
    }
}
