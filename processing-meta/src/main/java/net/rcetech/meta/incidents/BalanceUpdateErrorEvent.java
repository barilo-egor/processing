package net.rcetech.meta.incidents;

import java.util.UUID;

public record BalanceUpdateErrorEvent(
        UUID transactionId,
        UUID clientId,
        long errorTimestamp
) implements IncidentEvent {

    @Override
    public IncidentType type() {
        return IncidentType.BALANCE_UPDATE_ERROR;
    }
}
