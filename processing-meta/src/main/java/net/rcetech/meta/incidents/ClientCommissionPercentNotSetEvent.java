package net.rcetech.meta.incidents;

import java.util.UUID;

public record ClientCommissionPercentNotSetEvent(
        UUID clientId,
        long errorTimestamp
) implements IncidentEvent {

    @Override
    public IncidentType type() {
        return IncidentType.CLIENT_COMMISSION_PERCENT_NOT_SET;
    }
}
