package net.rcetech.orders.callback;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.model.orders.MerchantCallback;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.domain.service.orders.MerchantCallbackService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.exception.OrderStatusUpdateException;
import net.rcetech.meta.incidents.IncidentType;
import net.rcetech.meta.incidents.dto.MerchantCallbackHandleErrorData;
import net.rcetech.meta.incidents.dto.OrderStatusTransitionRejectedData;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChange;
import net.rcetech.meta.orders.OrderStatusChangeSource;
import net.rcetech.meta.orders.OrderStatusTransition;
import net.rcetech.orders.status.OrderStatusResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class MerchantCallbackProcessor {

    private final Map<Merchant, OrderStatusResolver> statusResolversMap;

    private final OrderService orderService;

    private final MerchantCallbackService merchantCallbackService;

    private final IncidentService incidentService;

    public MerchantCallbackProcessor(List<OrderStatusResolver> orderStatusResolvers, OrderService orderService,
                                     MerchantCallbackService merchantCallbackService,
                                     IncidentService incidentService) {
        this.orderService = orderService;
        this.merchantCallbackService = merchantCallbackService;
        this.incidentService = incidentService;
        this.statusResolversMap = new EnumMap<>(Merchant.class);
        for (OrderStatusResolver resolver : orderStatusResolvers) {
            for (Merchant merchant : resolver.getMerchants()) {
                statusResolversMap.put(merchant, resolver);
            }
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void process(UUID orderId) {
        Order order = orderService.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BaseException("Ордер не найден по идентификатору " + orderId));
        if (OrderStatus.SEARCHING.equals(order.getStatus())) {
            log.debug("Обработка КБ по ордеру {} отложена до завершения поиска реквизитов.", orderId);
            return;
        }
        if (Objects.isNull(order.getMerchant())) {
            log.debug("У ордера {} нет мерчанта, КБ не обрабатываются.", orderId);
            return;
        }
        Instant processedAt = Instant.now();
        for (MerchantCallback callback : merchantCallbackService.findAllUnprocessed(order)) {
            callback.setOrder(order);
            if (!routeOrder(order, callback)) {
                return;
            }
            callback.setProcessedAt(processedAt);
        }
    }

    private boolean routeOrder(Order order, MerchantCallback callback) {
        Optional<OrderStatus> status = resolveStatus(callback);
        if (status.isEmpty()) {
            reportUnresolvedStatus(order, callback);
            return false;
        }
        try {
            OrderStatusChange change = orderService.updateStatus(order.getId(), status.get(),
                    OrderStatusChangeSource.MERCHANT_CALLBACK, callback.getStatus());
            if (OrderStatusTransition.ALLOWED.equals(change.transition())) {
                log.info("Ордер {} переведён из статуса {} в статус {} по КБ мерчанта {} (статус {}).",
                        order.getId(), change.from(), change.to(), callback.getMerchant(), callback.getStatus());
            }
            return true;
        } catch (OrderStatusUpdateException e) {
            reportRejectedTransition(order, callback, e);
            return false;
        }
    }

    private Optional<OrderStatus> resolveStatus(MerchantCallback callback) {
        OrderStatusResolver resolver = statusResolversMap.get(callback.getMerchant());
        if (Objects.isNull(resolver)) {
            return Optional.empty();
        }
        return resolver.resolve(callback.getStatus());
    }

    private void reportUnresolvedStatus(Order order, MerchantCallback callback) {
        long timestamp = System.currentTimeMillis();
        log.error("{} Не удалось определить статус ордера {} по КБ мерчанта {} (статус {}).", timestamp,
                order.getId(), callback.getMerchant(), callback.getStatus());
        MerchantCallbackHandleErrorData data = new MerchantCallbackHandleErrorData(callback.getMerchant(),
                callback.getStatus(), callback.getStatusDescription(), callback.getMerchantOrderId());
        incidentService.save(IncidentType.MERCHANT_CALLBACK_HANDLE_ERROR, data, timestamp);
    }

    private void reportRejectedTransition(Order order, MerchantCallback callback, OrderStatusUpdateException e) {
        long timestamp = System.currentTimeMillis();
        log.warn("{} Переход ордера {} из статуса {} в статус {} по КБ мерчанта {} (статус {}) отклонён.",
                timestamp, order.getId(), e.getStatusFrom(), e.getStatusTo(), callback.getMerchant(),
                callback.getStatus());
        OrderStatusTransitionRejectedData data = new OrderStatusTransitionRejectedData(order.getId(),
                e.getStatusFrom().getDescription(), e.getStatusTo().getDescription(), callback.getMerchant(),
                callback.getStatus());
        incidentService.save(IncidentType.ORDER_STATUS_TRANSITION_REJECTED, data, timestamp);
    }
}
