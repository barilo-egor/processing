package net.rcetech.orders.callback;

import net.rcetech.domain.service.orders.ClientCallbackService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import net.rcetech.meta.orders.dto.ClientOrderSummary;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@Service
public class CallbackEnqueuer {

    private final OrderService orderService;

    private final ClientCallbackService clientCallbackService;

    private final ObjectMapper callbackMapper;

    public CallbackEnqueuer(OrderService orderService, ClientCallbackService clientCallbackService,
                            ObjectMapper callbackMapper) {
        this.orderService = orderService;
        this.clientCallbackService = clientCallbackService;
        this.callbackMapper = callbackMapper;
    }

    @EventListener
    public void enqueueCallback(OrderStatusUpdatedEvent event) {
        if (!event.notifyClient()) {
            return;
        }
        ClientOrderSummary order = orderService.findById(event.orderId(), ClientOrderSummary.class)
                .orElseThrow(() -> new BaseException("Ордер не найден."));
        if (Objects.isNull(order.callbackUrl())) {
            return;
        }
        clientCallbackService.create(order.id(), order.status(), order.callbackUrl(),
                callbackMapper.writeValueAsString(order));
    }
}
