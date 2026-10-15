package net.rcetech.meta.exception;

import lombok.Getter;
import net.rcetech.meta.orders.OrderStatus;

@Getter
public class OrderStatusUpdateException extends BadRequestException {

    private final OrderStatus statusFrom;

    private final OrderStatus statusTo;

    public OrderStatusUpdateException(String message, OrderStatus statusFrom, OrderStatus statusTo) {
        super(message);
        this.statusFrom = statusFrom;
        this.statusTo = statusTo;
    }
}
