package net.rcetech.meta.orders.dto;

import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.serialize.InstantToMillisSerializer;
import org.springframework.beans.factory.annotation.Value;
import tgb.cryptoexchange.commons.enums.Merchant;
import tools.jackson.databind.annotation.JsonSerialize;

import java.time.Instant;
import java.util.UUID;

public interface OrderSummary {
    UUID getId();
    @JsonSerialize(using = InstantToMillisSerializer.class)
    Instant getCreatedAt();
    @Value("#{target.client.id}")
    String getClientId();
    @Value("#{target.client.username}")
    String getClientUsername();
    String getInternalId();
    OrderStatus getStatus();
    Integer getAmount();
    Merchant getMerchant();
    String getMerchantOrderId();
}