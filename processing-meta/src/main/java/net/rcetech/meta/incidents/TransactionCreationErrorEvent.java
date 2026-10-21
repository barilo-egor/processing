package net.rcetech.meta.incidents;

import net.rcetech.meta.orders.OrderStatus;

import java.util.UUID;

public record TransactionCreationErrorEvent(
        UUID orderId,
        OrderStatus status,
        long errorTimestamp
) implements IncidentEvent {

    @Override
    public IncidentType type() {
        return IncidentType.TRANSACTION_CREATION_ERROR;
    }
}
