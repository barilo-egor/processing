package net.rcetech.meta.billing.dto;

import net.rcetech.meta.serialize.MillisToInstantDeserializer;
import tgb.cryptoexchange.commons.enums.Merchant;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;
import java.util.UUID;

public record MerchantCallbackFilter(
        UUID orderId,
        Merchant merchant,
        String merchantOrderId,
        @JsonDeserialize(using = MillisToInstantDeserializer.class)
        Instant createdAtFrom,
        @JsonDeserialize(using = MillisToInstantDeserializer.class)
        Instant createdAtTo
) {}
