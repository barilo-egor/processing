package net.rcetech.orders;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.incidents.IncidentType;
import net.rcetech.meta.incidents.dto.StuckSearchOrderErrorData;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChange;
import net.rcetech.meta.orders.OrderStatusChangeSource;
import net.rcetech.meta.orders.OrderStatusTransition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OrderSearchTimeoutProcessor {

    private static final int STUCK_SEARCH_MARGIN_SECONDS = 30;

    private final OrderFacade orderFacade;

    private final OrderService orderService;

    private final IncidentService incidentService;

    private final int stuckAfterSeconds;

    public OrderSearchTimeoutProcessor(OrderFacade orderFacade, OrderService orderService,
                                       IncidentService incidentService,
                                       @Value("${details-search.timeout.max-seconds:300}") int maxTimeoutSeconds) {
        this.orderFacade = orderFacade;
        this.orderService = orderService;
        this.incidentService = incidentService;
        this.stuckAfterSeconds = maxTimeoutSeconds + STUCK_SEARCH_MARGIN_SECONDS;
    }

    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void checkStuckInSearchOrders() {
        for (Order order : orderService.findAllInSearchCreatedBefore(Instant.now().minusSeconds(stuckAfterSeconds))) {
            try {
                OrderStatusChange change = orderFacade.routeOrder(OrderStatus.DETAILS_NOT_FOUND, order.getId(),
                        OrderStatusChangeSource.SYSTEM, null);
                if (OrderStatusTransition.ALLOWED.equals(change.transition())) {
                    log.warn("Ордер {} созданный {} завис в поиске реквизитов и переведён в статус {}.",
                            order.getId(), order.getCreatedAt(), change.to());
                }
            } catch (Exception e) {
                long timestamp = System.currentTimeMillis();
                log.error("{} Не удалось перевести зависший в поиске реквизитов ордер {}: {}", timestamp,
                        order.getId(), e.getMessage(), e);
                incidentService.save(IncidentType.STUCK_SEARCH_ORDER_ERROR,
                        new StuckSearchOrderErrorData(order.getId(), e.getMessage()), timestamp);
            }
        }
    }
}
