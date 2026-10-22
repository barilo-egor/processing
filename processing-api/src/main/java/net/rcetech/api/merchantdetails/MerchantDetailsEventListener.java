package net.rcetech.api.merchantdetails;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChangeSource;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class MerchantDetailsEventListener {

    private final ApiMerchantDetailsGrpcService apiMerchantDetailsGrpcService;

    private final OrderService orderService;

    public MerchantDetailsEventListener(ApiMerchantDetailsGrpcService apiMerchantDetailsGrpcService,
                                        OrderService orderService) {
        this.apiMerchantDetailsGrpcService = apiMerchantDetailsGrpcService;
        this.orderService = orderService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void cancelDetailsSearch(OrderStatusUpdatedEvent event) {
        if (OrderStatus.CANCELED.equals(event.orderStatus())) {
            apiMerchantDetailsGrpcService.cancelSearch(event.orderId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void orderStatusUpdated(OrderStatusUpdatedEvent event) {
        if (!OrderStatus.CANCELED.equals(event.orderStatus())
                || OrderStatusChangeSource.MERCHANT_CALLBACK.equals(event.source())) {
            return;
        }
        CancelOrderDTO cancelOrder = orderService.findById(event.orderId(), CancelOrderDTO.class).orElse(null);
        if (cancelOrder == null || cancelOrder.merchant() == null || cancelOrder.merchantOrderId() == null) {
            log.debug("Отмена ордера {} в merchant-details не требуется: мерчант или идентификатор ордера мерчанта " +
                    "не заданы.", event.orderId());
            return;
        }
        if (cancelOrder.merchantOrderId().startsWith(RandomDetailsReceiver.TEST_ORDER_ID_PREFIX)) {
            log.debug("Отмена тестового ордера {} в merchant-details не требуется.", event.orderId());
            return;
        }
        try {
            apiMerchantDetailsGrpcService.cancelOrder(cancelOrder);
        } catch (Exception e) {
            log.info("Не удалось отменить ордер {} мерчанта {} в merchant-details: {}",
                    cancelOrder.merchantOrderId(), cancelOrder.merchant(), e.getMessage());
        }
    }
}
