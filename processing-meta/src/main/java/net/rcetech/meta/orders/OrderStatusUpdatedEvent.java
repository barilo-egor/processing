package net.rcetech.meta.orders;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class OrderStatusUpdatedEvent extends ApplicationEvent {

    private final UUID orderId;

    private final OrderStatus orderStatus;

    public OrderStatusUpdatedEvent(Object source, UUID orderId, OrderStatus orderStatus) {
        super(source);
        this.orderId = orderId;
        this.orderStatus = orderStatus;
    }
}
