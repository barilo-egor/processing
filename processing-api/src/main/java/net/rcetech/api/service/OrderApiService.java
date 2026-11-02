package net.rcetech.api.service;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.api.dto.CreateOrderCommand;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.api.merchantdetails.ApiDetailsResponse;
import net.rcetech.api.merchantdetails.DetailsValidator;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.orders.OrderSpecifications;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.exception.OrderStatusUpdateException;
import net.rcetech.meta.exception.ServiceUnavailableException;
import net.rcetech.meta.incidents.AsyncDetailsSearchErrorEvent;
import net.rcetech.meta.orders.InvalidDetailsReceivedEvent;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChange;
import net.rcetech.meta.orders.OrderStatusChangeSource;
import net.rcetech.meta.orders.OrderStatusTransition;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.meta.orders.dto.ClientOrderFilter;
import net.rcetech.meta.orders.dto.ClientOrderSummary;
import net.rcetech.orders.OrderFacade;
import net.rcetech.orders.callback.MerchantCallbackProcessor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

@Service
@Slf4j
public class OrderApiService {

    private static final String ORDER_NOT_FOUND_MESSAGE = "Ордер не найден.";

    private final DetailsSearchService detailsSearchService;

    private final ClientService clientService;

    private final OrderService orderService;

    private final OrderFacade orderFacade;

    private final MerchantCallbackProcessor merchantCallbackProcessor;

    private final ApplicationEventPublisher eventPublisher;

    public OrderApiService(DetailsSearchService detailsSearchService, ClientService clientService,
                           OrderService orderService, OrderFacade orderFacade,
                           MerchantCallbackProcessor merchantCallbackProcessor,
                           ApplicationEventPublisher eventPublisher) {
        this.detailsSearchService = detailsSearchService;
        this.clientService = clientService;
        this.orderService = orderService;
        this.orderFacade = orderFacade;
        this.merchantCallbackProcessor = merchantCallbackProcessor;
        this.eventPublisher = eventPublisher;
    }

    public ClientOrderSummary createOrder(CreateOrderCommand command) {
        Client client = clientService.findById(command.clientId())
                .orElseThrow(() -> new BaseException("Клиент не найден по идентификатору " + command.clientId()));
        CreateOrderRequest request = command.request();
        Order order = orderService.createOrder(client, request.internalId(), request.amount(), request.callbackUrl());
        completeSearch(order, request.methods(), detailsSearchService.search(order, command), false);
        return findSummary(order.getId());
    }

    public ClientOrderSummary createOrderAsync(CreateOrderCommand command,
                                               BiConsumer<Void, Throwable> onSearchCompleted) {
        Client client = clientService.findById(command.clientId())
                .orElseThrow(() -> new BaseException("Клиент не найден по идентификатору " + command.clientId()));
        CreateOrderRequest request = command.request();
        Order order = orderService.createOrder(client, request.internalId(), request.amount(), request.callbackUrl());
        ClientOrderSummary summary = findSummary(order.getId());
        if (Objects.isNull(summary.callbackUrl())) {
            log.warn("Асинхронное создание ордера {} клиента {} без адреса КБ.", order.getId(), client.getId());
        }
        detailsSearchService.searchAsync(order, command)
                .thenAccept(details -> completeSearch(order, request.methods(), details, true))
                .exceptionally(e -> {
                    Throwable cause = e.getCause();
                    long timestamp = System.currentTimeMillis();
                    log.error("{} Ошибка асинхронного поиска реквизитов по ордеру {}: {}", timestamp, order.getId(),
                            cause.getMessage(), cause);
                    eventPublisher.publishEvent(new AsyncDetailsSearchErrorEvent(order.getId(), cause.getMessage(),
                            timestamp));
                    return null;
                })
                .whenComplete(onSearchCompleted);
        return summary;
    }

    private ClientOrderSummary findSummary(UUID orderId) {
        return orderService.findById(orderId, ClientOrderSummary.class)
                .orElseThrow(() -> new BaseException("Ордер не найден по идентификатору " + orderId));
    }

    private void completeSearch(Order order, Set<RequestMethod> requestedMethods,
                                Optional<ApiDetailsResponse> details, boolean notifyClient) {
        if (details.isEmpty()) {
            orderFacade.routeOrder(OrderStatus.DETAILS_NOT_FOUND, order.getId(), OrderStatusChangeSource.SYSTEM,
                    notifyClient, routed -> { });
            return;
        }
        ApiDetailsResponse detailsResponse = details.get();
        Optional<String> violation = DetailsValidator.validate(detailsResponse, requestedMethods);
        if (violation.isPresent()) {
            rejectDetails(order, detailsResponse, violation.get(), notifyClient);
            return;
        }
        OrderStatusChange change = orderFacade.routeOrder(OrderStatus.NEW, order.getId(),
                OrderStatusChangeSource.SYSTEM, notifyClient,
                routed -> fillDetails(routed, detailsResponse, order.getClient().getOrderTimeoutSeconds()));
        if (!OrderStatusTransition.ALLOWED.equals(change.transition())) {
            log.info("Реквизиты по ордеру {} найдены, но ордер уже в статусе {}.", order.getId(), change.from());
            return;
        }
        processMerchantCallbacks(order.getId());
    }

    private void rejectDetails(Order order, ApiDetailsResponse detailsResponse, String reason, boolean notifyClient) {
        long timestamp = System.currentTimeMillis();
        log.error("{} Мерчант {} выдал некорректные реквизиты по ордеру {} (идентификатор ордера мерчанта {}): {}.",
                timestamp, detailsResponse.merchant(), order.getId(), detailsResponse.orderId(), reason);
        RequestMethod method = Objects.isNull(detailsResponse.details()) ? null
                : detailsResponse.details().requestMethod();
        eventPublisher.publishEvent(new InvalidDetailsReceivedEvent(order.getId(), order.getClient().getId(),
                detailsResponse.merchant(), detailsResponse.orderId(), method, reason, timestamp));
        orderFacade.routeOrder(OrderStatus.DETAILS_NOT_FOUND, order.getId(), OrderStatusChangeSource.SYSTEM,
                notifyClient, routed -> { });
        if (!notifyClient) {
            throw new ServiceUnavailableException("Мерчант " + detailsResponse.merchant()
                    + " выдал некорректные реквизиты по ордеру " + order.getId() + ": " + reason + ".");
        }
    }

    private void processMerchantCallbacks(UUID orderId) {
        try {
            merchantCallbackProcessor.process(orderId);
        } catch (Exception e) {
            long timestamp = System.currentTimeMillis();
            log.error("{} Ошибка обработки КБ мерчанта, пришедших во время поиска реквизитов по ордеру {}: {}",
                    timestamp, orderId, e.getMessage(), e);
        }
    }

    private void fillDetails(Order order, ApiDetailsResponse detailsResponse, int orderTimeoutSeconds) {
        ApiDetailsResponse.Details details = detailsResponse.details();
        order.setExpiresAt(Instant.now().plusSeconds(orderTimeoutSeconds));
        if (Objects.nonNull(detailsResponse.amount())) {
            order.setAmount(detailsResponse.amount());
        }
        order.setMerchant(detailsResponse.merchant());
        order.setMerchantOrderId(detailsResponse.orderId());
        order.setMerchantOrderStatus(detailsResponse.orderStatus());
        order.setMethod(details.requestMethod());
        order.setDetails(details.details());
        order.setBank(details.bank());
    }

    public ClientOrderSummary findByIdAndClientId(UUID clientId, UUID id) {
        return orderService.findByIdAndClientId(id, clientId, ClientOrderSummary.class)
                .orElseThrow(() -> new BadRequestException(ORDER_NOT_FOUND_MESSAGE));
    }

    public void cancelOrder(UUID clientId, UUID id) {
        if (!orderService.existsByIdAndClientId(id, clientId)) {
            throw new BadRequestException(ORDER_NOT_FOUND_MESSAGE);
        }
        OrderStatusChange change = orderFacade.routeOrder(OrderStatus.CANCELED, id, OrderStatusChangeSource.CLIENT,
                null);
        if (OrderStatusTransition.SKIPPED.equals(change.transition())) {
            throw OrderStatusUpdateException.skipped(change);
        }
    }

    public Page<ClientOrderSummary> findAll(UUID clientId, ClientOrderFilter filter, Pageable pageable) {
        return orderService.findAll(OrderSpecifications.matches(clientId, filter), pageable, ClientOrderSummary.class);
    }

}
