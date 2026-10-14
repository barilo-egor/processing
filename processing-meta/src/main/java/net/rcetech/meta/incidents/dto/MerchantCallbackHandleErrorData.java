package net.rcetech.meta.incidents.dto;

import tgb.cryptoexchange.commons.enums.Merchant;

public record MerchantCallbackHandleErrorData(
        Merchant merchant,
        String status,
        String statusDescription,
        String merchantOrderId
) {}
