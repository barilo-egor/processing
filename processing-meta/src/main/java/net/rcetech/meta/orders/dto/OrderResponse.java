package net.rcetech.meta.orders.dto;

import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.meta.serialize.InstantToMillisSerializer;
import tgb.cryptoexchange.commons.enums.Merchant;
import tools.jackson.databind.annotation.JsonSerialize;

import java.time.Instant;
import java.util.UUID;

/**
 * Описание полей представлено в сущности Order
 */
public record OrderResponse(
        UUID id,
        @JsonSerialize(using = InstantToMillisSerializer.class)
        Instant createdAt,
        @JsonSerialize(using = InstantToMillisSerializer.class)
        Instant expiresAt,
        UUID clientId,
        String internalId,
        OrderStatus status,
        Integer amount,
        Boolean enableUniqueAmount,
        Merchant merchant,
        String merchantOrderId,
        String merchantOrderStatus,
        RequestMethod method,
        String details,
        String bank,
        String callbackUrl
) {
}
