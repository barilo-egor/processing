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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
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

    private final TransactionTemplate transactionTemplate;

    public MerchantCallbackProcessor(List<OrderStatusResolver> orderStatusResolvers, OrderService orderService,
                                     MerchantCallbackService merchantCallbackService,
                                     IncidentService incidentService, PlatformTransactionManager transactionManager) {
        this.orderService = orderService;
        this.merchantCallbackService = merchantCallbackService;
        this.incidentService = incidentService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.statusResolversMap = new EnumMap<>(Merchant.class);
        for (OrderStatusResolver resolver : orderStatusResolvers) {
            for (Merchant merchant : resolver.getMerchants()) {
                statusResolversMap.put(merchant, resolver);
            }
        }
    }

    public void process(UUID orderId) {
        Order order = orderService.findById(orderId)
                .orElseThrow(() -> new BaseException("Ордер не найден по идентификатору " + orderId));
        if (OrderStatus.SEARCHING.equals(order.getStatus())) {
            log.debug("Обработка КБ по ордеру {} отложена до завершения поиска реквизитов.", orderId);
            return;
        }
        if (Objects.isNull(order.getMerchant())) {
            log.debug("У ордера {} нет мерчанта, КБ не обрабатываются.", orderId);
            return;
        }
        for (MerchantCallback callback : merchantCallbackService.findAllUnprocessed(order)) {
            try {
                transactionTemplate.executeWithoutResult(status -> processCallback(orderId, callback.getId()));
            } catch (Exception e) {
                reportProcessingError(orderId, callback, e);
            }
        }
    }

    private void processCallback(UUID orderId, Long callbackId) {
        Order order = orderService.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BaseException("Ордер не найден по идентификатору " + orderId));
        MerchantCallback callback = merchantCallbackService.findById(callbackId)
                .orElseThrow(() -> new BaseException("КБ мерчанта не найден по идентификатору " + callbackId));
        if (Objects.nonNull(callback.getProcessedAt())) {
            return;
        }
        callback.setOrder(order);
        callback.setProcessedAt(Instant.now());
        routeOrder(order, callback);
    }

    private void routeOrder(Order order, MerchantCallback callback) {
        Optional<OrderStatus> status = resolveStatus(callback);
        if (status.isEmpty()) {
            reportUnresolvedStatus(order, callback);
            return;
        }
        try {
            OrderStatusChange change = orderService.updateStatus(order.getId(), status.get(),
                    OrderStatusChangeSource.MERCHANT_CALLBACK, callback.getStatus());
            if (OrderStatusTransition.ALLOWED.equals(change.transition())) {
                log.info("Ордер {} переведён из статуса {} в статус {} по КБ мерчанта {} (статус {}).",
                        order.getId(), change.from(), change.to(), callback.getMerchant(), callback.getStatus());
            }
        } catch (OrderStatusUpdateException e) {
            reportRejectedTransition(order, callback, e);
        }
    }

    private Optional<OrderStatus> resolveStatus(MerchantCallback callback) {
        OrderStatusResolver resolver = statusResolversMap.get(callback.getMerchant());
        if (Objects.isNull(resolver)) {
            return Optional.empty();
        }
        return resolver.resolve(callback.getStatus());
    }

    private void reportProcessingError(UUID orderId, MerchantCallback callback, Exception e) {
        long timestamp = System.currentTimeMillis();
        log.error("{} Ошибка обработки КБ мерчанта {} по ордеру {}: {}", timestamp, callback.getId(), orderId,
                e.getMessage(), e);
        MerchantCallbackHandleErrorData data = new MerchantCallbackHandleErrorData(callback.getMerchant(),
                callback.getStatus(), callback.getStatusDescription(), callback.getMerchantOrderId());
        incidentService.save(IncidentType.MERCHANT_CALLBACK_HANDLE_ERROR, data, timestamp);
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
