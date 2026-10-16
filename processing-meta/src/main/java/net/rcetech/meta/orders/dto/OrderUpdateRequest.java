package net.rcetech.meta.orders.dto;

import jakarta.validation.constraints.NotNull;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.validation.OrderStatusSubset;

public record OrderUpdateRequest(
        @NotNull(message = "Статус ордера обязателен.")
        @OrderStatusSubset(anyOf = {OrderStatus.SUCCESS, OrderStatus.CANCELED, OrderStatus.DISPUTE})
        OrderStatus status
) { }
