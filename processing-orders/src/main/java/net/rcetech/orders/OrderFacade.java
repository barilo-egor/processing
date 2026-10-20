package net.rcetech.orders;

import jakarta.persistence.OptimisticLockException;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChange;
import net.rcetech.meta.orders.OrderStatusChangeSource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class OrderFacade {
    
    private final OrderService orderService;

    public OrderFacade(OrderService orderService) {
        this.orderService = orderService;
    }

    public Optional<Order> getOrderByIdOrMerchantOrderId(Merchant merchant, String id) {
        Optional<Order> maybeOrder = orderService.findByMerchantAndMerchantOrderId(merchant, id);
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
    public OrderStatusChange routeOrder(OrderStatus orderStatus, UUID orderId, OrderStatusChangeSource source,
                                        String merchantOrderStatus) {
        return orderService.updateStatus(orderId, orderStatus, source, merchantOrderStatus);
    }

    @Retryable(value = {OptimisticLockException.class, OptimisticLockingFailureException.class},
            jitter = 200, delay = 500, maxDelay = 600)
    public OrderStatusChange routeOrder(OrderStatus orderStatus, UUID orderId, OrderStatusChangeSource source,
                                        boolean notifyClient, Consumer<Order> changes) {
        return orderService.updateStatus(orderId, orderStatus, source, notifyClient, changes);
    }
}
