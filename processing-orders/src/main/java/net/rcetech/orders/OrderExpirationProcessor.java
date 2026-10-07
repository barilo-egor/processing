package net.rcetech.orders;

import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.service.orders.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
            orderFacade.timeoutOrder(order.getId());
        }
    }
}
