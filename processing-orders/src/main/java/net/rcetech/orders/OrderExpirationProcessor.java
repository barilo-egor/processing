package net.rcetech.orders;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.incidents.OrderExpirationErrorEvent;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChange;
import net.rcetech.meta.orders.OrderStatusChangeSource;
import net.rcetech.meta.orders.OrderStatusTransition;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OrderExpirationProcessor {

    private final OrderFacade orderFacade;

    private final OrderService orderService;

    private final ApplicationEventPublisher eventPublisher;

    public OrderExpirationProcessor(OrderFacade orderFacade, OrderService orderService,
                                    ApplicationEventPublisher eventPublisher) {
        this.orderFacade = orderFacade;
        this.orderService = orderService;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = "OrderExpirationProcessor.checkExpiredOrders", lockAtMostFor = "PT1M", lockAtLeastFor = "PT4S")
    public void checkExpiredOrders() {
        List<Order> newOrders = orderService.findAllExpired();
        for (Order order : newOrders) {
            try {
                OrderStatusChange change = orderFacade.routeOrder(OrderStatus.TIMEOUT, order.getId(),
                        OrderStatusChangeSource.SYSTEM, null);
                if (OrderStatusTransition.ALLOWED.equals(change.transition())) {
                    log.info("Ордер {} клиента {} переведён по таймауту из статуса {} в статус {}.",
                            order.getId(), order.getClient().getId(), change.from(), change.to());
                }
            } catch (Exception e) {
                long timestamp = System.currentTimeMillis();
                log.error("{} Не удалось перевести просроченный ордер {} в статус {}: {}", timestamp, order.getId(),
                        OrderStatus.TIMEOUT, e.getMessage(), e);
                eventPublisher.publishEvent(new OrderExpirationErrorEvent(order.getId(), e.getMessage(), timestamp));
            }
        }
    }
}
