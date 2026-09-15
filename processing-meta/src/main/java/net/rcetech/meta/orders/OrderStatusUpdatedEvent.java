package net.rcetech.meta.orders;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class OrderStatusUpdatedEvent extends ApplicationEvent {

    private final UUID orderId;

    public OrderStatusUpdatedEvent(Object source, UUID orderId) {
        super(source);
        this.orderId = orderId;
    }
}
