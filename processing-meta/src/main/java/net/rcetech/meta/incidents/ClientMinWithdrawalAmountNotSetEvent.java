package net.rcetech.meta.incidents;

import java.util.UUID;

public record ClientMinWithdrawalAmountNotSetEvent(
        UUID clientId,
        long errorTimestamp
) implements ClientIncidentEvent {

    @Override
    public IncidentType type() {
        return IncidentType.CLIENT_MIN_WITHDRAWAL_AMOUNT_NOT_SET;
    }

    @Override
    public boolean onlyIfNoUnresolved() {
        return true;
    }
}
