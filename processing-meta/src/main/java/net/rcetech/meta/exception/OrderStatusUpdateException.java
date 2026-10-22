package net.rcetech.meta.exception;

import lombok.Getter;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChange;

@Getter
public class OrderStatusUpdateException extends BadRequestException {

    private final OrderStatus statusFrom;

    private final OrderStatus statusTo;

    public OrderStatusUpdateException(String message, OrderStatus statusFrom, OrderStatus statusTo) {
        super(message);
        this.statusFrom = statusFrom;
        this.statusTo = statusTo;
    }

    public static OrderStatusUpdateException skipped(OrderStatusChange change) {
        String message = change.from().equals(change.to())
                ? "Ордер уже в статусе «" + change.to().getDescription() + "»."
                : "Переход ордера из статуса «" + change.from().getDescription() + "» в статус «"
                + change.to().getDescription() + "» не выполняется.";
        return new OrderStatusUpdateException(message, change.from(), change.to());
    }
}
