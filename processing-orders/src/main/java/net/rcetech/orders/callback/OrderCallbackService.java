package net.rcetech.orders.callback;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.orders.MerchantCallbackEvent;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.orders.status.OrderStatusResolver;
import org.springframework.stereotype.Service;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.*;

@Service
@Slf4j
public class OrderCallbackService {

    private final Map<Merchant, OrderStatusResolver> statusResolversMap;

    private final OrderService orderService;

    public OrderCallbackService(List<OrderStatusResolver> orderStatusResolver, OrderService orderService) {
        this.orderService = orderService;
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
        Optional<Order> maybeOrder = orderService.findByMerchantOrderId(id);
        if (maybeOrder.isEmpty()) {
            try {
                maybeOrder = orderService.findById(UUID.fromString(id));
            } catch (IllegalArgumentException e) {
                maybeOrder = Optional.empty();
            }
        }
        if (maybeOrder.isPresent()) {
            Order order = maybeOrder.get();
            Optional<OrderStatus> maybeStatus = statusResolversMap.get(merchantCallbackEvent.getMerchant())
                    .resolve(merchantCallbackEvent.getStatus());
            if (maybeStatus.isEmpty()) {
                // TODO Создание инцидента
                throw new BaseException("Cant resolve status " + merchantCallbackEvent.getStatus()
                        + ", merchant " + merchantCallbackEvent.getMerchant().name()
                        + ", merchant order id " + merchantCallbackEvent.getMerchantOrderId()
                        + ", order id " + order.getId());
            }
            switch (maybeStatus.get()) {
                case SUCCESS -> orderService.confirm(order.getId(), merchantCallbackEvent.getStatus());
                case CANCELED -> orderService.cancel(order.getId(), merchantCallbackEvent.getStatus());
                case TIMEOUT -> orderService.timeout(order.getId(), merchantCallbackEvent.getStatus());
                case DISPUTE ->  orderService.dispute(order.getId(), merchantCallbackEvent.getStatus());
                default -> log.error("Не определен статус коллбэка: {}", merchantCallbackEvent); // TODO Создание инцидента
            }
        } else {
            log.debug("Ордер для callback id={} не найден.", id);
        }
    }
}
