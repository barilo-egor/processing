package net.rcetech.meta.incidents;

import tgb.cryptoexchange.commons.enums.Merchant;

public record MerchantCallbackHandleErrorEvent(
        Merchant merchant,
        String status,
        String statusDescription,
        String merchantOrderId,
        long errorTimestamp
) implements IncidentEvent {

    @Override
    public IncidentType type() {
        return IncidentType.MERCHANT_CALLBACK_HANDLE_ERROR;
    }
}
