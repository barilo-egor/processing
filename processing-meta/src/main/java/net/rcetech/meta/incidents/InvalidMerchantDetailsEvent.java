package net.rcetech.meta.incidents;

import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.UUID;

public record InvalidMerchantDetailsEvent(
        UUID orderId,
        UUID clientId,
        Merchant merchant,
        String reason,
        String merchantSwitchResult,
        long errorTimestamp
) implements OrderIncidentEvent {

    @Override
    public IncidentType type() {
        return IncidentType.INVALID_MERCHANT_DETAILS;
    }
}
