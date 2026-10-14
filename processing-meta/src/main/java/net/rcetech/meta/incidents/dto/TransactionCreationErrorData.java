package net.rcetech.meta.incidents.dto;

import net.rcetech.meta.orders.OrderStatus;

import java.util.UUID;

public record TransactionCreationErrorData(
        UUID orderId, OrderStatus status
) {}
