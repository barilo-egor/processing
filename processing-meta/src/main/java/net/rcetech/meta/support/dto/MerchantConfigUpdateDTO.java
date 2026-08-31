package net.rcetech.meta.support.dto;

import tgb.cryptoexchange.commons.enums.Merchant;

public record MerchantConfigUpdateDTO(
        Boolean isOn,
        Merchant merchant,
        Integer maxAmount,
        Integer minAmount
) {}
