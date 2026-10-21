package net.rcetech.meta.incidents;

import net.rcetech.meta.orders.OrderStatus;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.UUID;

public record OrderStatusTransitionRejectedEvent(
        UUID orderId,
        OrderStatus statusFrom,
        OrderStatus statusTo,
        Merchant merchant,
        String merchantOrderStatus,
        long errorTimestamp
) implements IncidentEvent {

    @Override
    public IncidentType type() {
        return IncidentType.ORDER_STATUS_TRANSITION_REJECTED;
    }
}
