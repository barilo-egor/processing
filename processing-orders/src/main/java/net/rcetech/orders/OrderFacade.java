package net.rcetech.orders;

import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.orders.OrderStatus;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class OrderFacade {
    
    private final OrderService orderService;

    public OrderFacade(OrderService orderService) {
        this.orderService = orderService;
    }

    public Optional<Order> getOrderByIdOrMerchantOrderId(String id) {
        Optional<Order> maybeOrder = orderService.findByMerchantOrderId(id);
        if (maybeOrder.isEmpty()) {
            try {
                maybeOrder = orderService.findById(UUID.fromString(id));
            } catch (IllegalArgumentException e) {
                maybeOrder = Optional.empty();
            }
        }
        return maybeOrder;
    }

    @Retryable(value = OptimisticLockException.class, jitter = 200, delay = 500, maxDelay = 600)
    public void routeOrder(OrderStatus orderStatus, Order order, String merchantOrderStatus) {
        switch (orderStatus) {
            case SUCCESS -> orderService.confirm(order.getId(), merchantOrderStatus);
            case CANCELED -> orderService.cancel(order.getId(), merchantOrderStatus);
            case TIMEOUT -> orderService.timeout(order.getId(), merchantOrderStatus);
            case DISPUTE ->  orderService.dispute(order.getId(), merchantOrderStatus);
            default -> log.error("Не определен статус коллбэка: {}", merchantOrderStatus); // TODO Создание инцидента
        }
    }

    @Retryable(value = OptimisticLockException.class, jitter = 200, delay = 500, maxDelay = 600)
    public void timeoutOrder(UUID orderId) {
        orderService.timeout(orderId, null);
    }
}
