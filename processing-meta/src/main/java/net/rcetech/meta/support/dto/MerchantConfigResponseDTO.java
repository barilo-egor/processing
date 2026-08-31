package net.rcetech.meta.support.dto;

import tgb.cryptoexchange.commons.enums.Merchant;

public record MerchantConfigResponseDTO(
        Long id,
        Boolean isOn,
        Merchant merchant,
        Integer maxAmount,
        Integer minAmount
) {}
