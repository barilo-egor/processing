package net.rcetech.orders;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChange;
import net.rcetech.meta.orders.OrderStatusTransition;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OrderExpirationProcessor {

    private final OrderFacade orderFacade;

    private final OrderService orderService;

    public OrderExpirationProcessor(OrderFacade orderFacade, OrderService orderService) {
        this.orderFacade = orderFacade;
        this.orderService = orderService;
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    public void checkExpiredOrders() {
        List<Order> newOrders = orderService.findAllExpired();
        for (Order order : newOrders) {
            OrderStatusChange change = orderFacade.routeOrder(OrderStatus.TIMEOUT, order.getId(), null);
            if (OrderStatusTransition.ALLOWED.equals(change.transition())) {
                log.info("Ордер {} клиента {} переведён по таймауту из статуса {} в статус {}.",
                        order.getId(), order.getClient().getId(), change.from(), change.to());
            }
        }
    }
}
