package net.rcetech.meta.billing.dto;

import net.rcetech.meta.serialize.InstantToMillisSerializer;
import tgb.cryptoexchange.commons.enums.Merchant;
import tools.jackson.databind.annotation.JsonSerialize;

import java.time.Instant;
import java.util.UUID;

public record MerchantCallbackResponse(
        Long id,
        @JsonSerialize(using = InstantToMillisSerializer.class)
        Instant createdAt,
        UUID orderId,
        Merchant merchant,
        String merchantOrderId,
        String status,
        String statusDescription
) {}
