package net.rcetech.tg;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChangeSource;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class OrderDisputeListener {

    private static final String ORDERS_PATH = "/support/dashboard?section=orders";

    private final MethodExecutor methodExecutor;

    private final Long chatId;

    private final String ordersUrl;

    public OrderDisputeListener(MethodExecutor methodExecutor,
                                @Value("${tg.bot.group-chat-id}") Long chatId,
                                @Value("${processing.uri.base-url}") String baseUrl) {
        this.methodExecutor = methodExecutor;
        this.chatId = chatId;
        this.ordersUrl = baseUrl + ORDERS_PATH;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void orderStatusUpdated(OrderStatusUpdatedEvent event) {
        if (!OrderStatus.DISPUTE.equals(event.orderStatus())
                || !OrderStatusChangeSource.MERCHANT_CALLBACK.equals(event.source())) {
            return;
        }
        try {
            methodExecutor.sendMessage(chatId, "Мерчант открыл спор по ордеру " + event.orderId() + ".\n" + ordersUrl);
        } catch (Exception e) {
            log.error("Ошибка отправки уведомления о споре по ордеру {}: {}", event.orderId(), e.getMessage(), e);
        }
    }
}
