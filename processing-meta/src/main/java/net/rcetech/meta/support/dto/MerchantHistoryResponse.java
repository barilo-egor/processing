package net.rcetech.meta.support.dto;

import net.rcetech.meta.serialize.InstantToMillisSerializer;
import tgb.cryptoexchange.commons.enums.Merchant;
import tools.jackson.databind.annotation.JsonSerialize;

import java.time.Instant;

public record MerchantHistoryResponse(
        String operationId,
        String actorId,
        String initiatorApp,
        @JsonSerialize(using = InstantToMillisSerializer.class)
        Instant createdAt,
        Merchant merchant,
        String merchantOrderId,
        Integer requestedAmount,
        Integer merchantAmount,
        String method,
        String details
) {}
