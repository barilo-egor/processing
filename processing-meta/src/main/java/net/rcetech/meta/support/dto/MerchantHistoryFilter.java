package net.rcetech.meta.support.dto;

import net.rcetech.meta.serialize.MillisToInstantDeserializer;
import tgb.cryptoexchange.commons.enums.Merchant;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;
import java.util.List;

public record MerchantHistoryFilter(
        String orderId,
        String operationId,
        String actorId,
        @JsonDeserialize(using = MillisToInstantDeserializer.class)
        Instant createdAtFrom,
        @JsonDeserialize(using = MillisToInstantDeserializer.class)
        Instant createdAtTo,
        String initiatorApp,
        String details,
        List<Merchant> merchants,
        Integer merchantAmount,
        Integer requestedAmount
) {

}
