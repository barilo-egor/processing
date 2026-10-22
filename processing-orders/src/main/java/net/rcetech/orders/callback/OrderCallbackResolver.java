package net.rcetech.orders.callback;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.meta.orders.MerchantCallbackEvent;
import net.rcetech.orders.OrderFacade;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OrderCallbackResolver {

    private final OrderFacade orderFacade;

    private final MerchantCallbackProcessor merchantCallbackProcessor;

    public OrderCallbackResolver(OrderFacade orderFacade, MerchantCallbackProcessor merchantCallbackProcessor) {
        this.orderFacade = orderFacade;
        this.merchantCallbackProcessor = merchantCallbackProcessor;
    }

    public void resolve(MerchantCallbackEvent merchantCallbackEvent) {
        String id = merchantCallbackEvent.getMerchantOrderId();
        orderFacade.getOrderByIdOrMerchantOrderId(merchantCallbackEvent.getMerchant(), id).ifPresentOrElse(
                order -> merchantCallbackProcessor.process(order.getId()),
                () -> log.debug("Ордер для КБ мерчанта {} с id={} не найден, КБ сохранён без обработки.",
                        merchantCallbackEvent.getMerchant(), id)
        );
    }
}
