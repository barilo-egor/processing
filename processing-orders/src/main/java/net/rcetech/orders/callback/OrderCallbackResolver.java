package net.rcetech.orders.callback;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.service.orders.MerchantCallbackService;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.orders.MerchantCallbackEvent;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.orders.OrderFacade;
import net.rcetech.orders.status.OrderStatusResolver;
import org.springframework.stereotype.Service;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class OrderCallbackResolver {

    private final Map<Merchant, OrderStatusResolver> statusResolversMap;

    private final OrderFacade orderFacade;

    private final MerchantCallbackService merchantCallbackService;

    public OrderCallbackResolver(List<OrderStatusResolver> orderStatusResolver, OrderFacade orderFacade,
                                 MerchantCallbackService merchantCallbackService) {
        this.orderFacade = orderFacade;
        this.merchantCallbackService = merchantCallbackService;
        this.statusResolversMap = new EnumMap<>(Merchant.class);
        for (OrderStatusResolver resolver : orderStatusResolver) {
            for (Merchant merchant : resolver.getMerchants()) {
                statusResolversMap.put(merchant, resolver);
            }
        }
    }

    /**
     * Определяет статус ордера и подтверждает, либо отменяет его.
     *
     * @param merchantCallbackEvent объект представляющий коллбэк
     */
    public void resolve(MerchantCallbackEvent merchantCallbackEvent) {
        String id = merchantCallbackEvent.getMerchantOrderId();
        Optional<Order> maybeOrder = orderFacade.getOrderByIdOrMerchantOrderId(id);
        if (maybeOrder.isEmpty()) {
            log.trace("Ордер для callback id={} не найден.", id);
            return;
        }
        Order order = maybeOrder.get();
        try {
            merchantCallbackService.save(order, merchantCallbackEvent);
        } catch (Exception e) {
            log.error("Ошибка при сохранении кб: {}", e.getMessage(), e);
        }
        Optional<OrderStatus> maybeStatus = statusResolversMap.get(merchantCallbackEvent.getMerchant())
                .resolve(merchantCallbackEvent.getStatus());
        if (maybeStatus.isEmpty()) {
            throw new BaseException("Cant resolve status " + merchantCallbackEvent.getStatus()
                    + ", merchant " + merchantCallbackEvent.getMerchant().name()
                    + ", merchant order id " + merchantCallbackEvent.getMerchantOrderId()
                    + ", order id " + order.getId());
        }
        orderFacade.routeOrder(maybeStatus.get(), order, merchantCallbackEvent.getStatus());
    }
}
