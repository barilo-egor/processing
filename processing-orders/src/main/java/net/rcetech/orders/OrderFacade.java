package net.rcetech.orders;

import jakarta.persistence.OptimisticLockException;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.orders.OrderStatus;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
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

    @Retryable(value = {OptimisticLockException.class, OptimisticLockingFailureException.class},
            jitter = 200, delay = 500, maxDelay = 600)
    public void routeOrder(OrderStatus orderStatus, UUID orderId, String merchantOrderStatus) {
        orderService.updateStatus(orderId, orderStatus, merchantOrderStatus);
    }
}
