package net.rcetech.meta.orders;

import tgb.cryptoexchange.commons.enums.Merchant;

public record OrderCanceledEvent(
        Merchant merchant,
        String merchantOrderId,
        RequestMethod method
) {}
