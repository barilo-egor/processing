package net.rcetech.meta.incidents.dto;

import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.UUID;

public record OrderStatusTransitionRejectedData(
        UUID orderId,
        String statusFrom,
        String statusTo,
        Merchant merchant,
        String merchantOrderStatus
) {}
