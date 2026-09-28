package net.rcetech.orders.callback;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import net.rcetech.meta.orders.dto.ClientOrderSummary;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

@Slf4j
@Service
public class CallbackSender {

    private final OrderService orderService;

    private final CallbackHttpSender callbackHttpSender;

    public CallbackSender(OrderService orderService, CallbackHttpSender callbackHttpSender) {
        this.orderService = orderService;
        this.callbackHttpSender = callbackHttpSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void sendCallback(OrderStatusUpdatedEvent event) {
        ClientOrderSummary order = orderService.findById(event.getOrderId(), ClientOrderSummary.class)
                .orElseThrow(() -> new BaseException("Ордер не найден."));
        String callbackUrl = order.callbackUrl();
        if (Objects.isNull(callbackUrl)) {
            return;
        }
        try {
            callbackHttpSender.send(order);
        } catch (Exception e) {
            log.error("Не получилось отправить КБ по ордеру {}: {}", order.id(), e.getMessage(), e);
        }
    }
}
