package net.rcetech.api.service;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.api.config.DetailsSearchExecutorConfig;
import net.rcetech.api.dto.CreateOrderCommand;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.api.merchantdetails.ApiDetailsResponse;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.orders.OrderSpecifications;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.exception.OrderStatusUpdateException;
import net.rcetech.meta.exception.ServiceUnavailableException;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChange;
import net.rcetech.meta.orders.OrderStatusChangeSource;
import net.rcetech.meta.orders.OrderStatusTransition;
import net.rcetech.meta.orders.dto.ClientOrderFilter;
import net.rcetech.meta.orders.dto.ClientOrderSummary;
import net.rcetech.orders.OrderFacade;
import net.rcetech.orders.callback.MerchantCallbackProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class OrderApiService {

    private static final String ORDER_NOT_FOUND_MESSAGE = "Order not found";

    private final DetailsSearchService detailsSearchService;

    private final ClientService clientService;

    private final OrderService orderService;

    private final OrderFacade orderFacade;

    private final MerchantCallbackProcessor merchantCallbackProcessor;

    private final TaskExecutor detailsSearchExecutor;

    public OrderApiService(DetailsSearchService detailsSearchService, ClientService clientService,
                           OrderService orderService, OrderFacade orderFacade,
                           MerchantCallbackProcessor merchantCallbackProcessor,
                           @Qualifier(DetailsSearchExecutorConfig.DETAILS_SEARCH_EXECUTOR)
                           TaskExecutor detailsSearchExecutor) {
        this.detailsSearchService = detailsSearchService;
        this.clientService = clientService;
        this.orderService = orderService;
        this.orderFacade = orderFacade;
        this.merchantCallbackProcessor = merchantCallbackProcessor;
        this.detailsSearchExecutor = detailsSearchExecutor;
    }

    public ClientOrderSummary createOrder(CreateOrderCommand command) {
        Client client = clientService.findById(command.clientId())
                .orElseThrow(() -> new BaseException("Клиент не найден по идентификатору " + command.clientId()));
        CreateOrderRequest request = command.request();
        Order order = orderService.createOrder(client, request.internalId(), request.amount(), request.callbackUrl());
        searchDetails(command, order.getId(), client.getOrderTimeoutSeconds(), false);
        return findSummary(order.getId());
    }

    public ClientOrderSummary createOrderAsync(CreateOrderCommand command) {
        Client client = clientService.findById(command.clientId())
                .orElseThrow(() -> new BaseException("Клиент не найден по идентификатору " + command.clientId()));
        CreateOrderRequest request = command.request();
        Order order = orderService.createOrder(client, request.internalId(), request.amount(), request.callbackUrl());
        ClientOrderSummary summary = findSummary(order.getId());
        submitSearch(command, order.getId(), client.getOrderTimeoutSeconds());
        if (Objects.isNull(summary.callbackUrl())) {
            log.warn("Асинхронное создание ордера {} клиента {} без адреса КБ.", order.getId(), client.getId());
        }
        return summary;
    }

    private void submitSearch(CreateOrderCommand command, UUID orderId, int orderTimeoutSeconds) {
        try {
            detailsSearchExecutor.execute(() -> {
                try {
                    searchDetails(command, orderId, orderTimeoutSeconds, true);
                } catch (Exception e) {
                    log.error("Ошибка асинхронного поиска реквизитов по ордеру {}: {}", orderId, e.getMessage(), e);
                }
            });
        } catch (TaskRejectedException e) {
            orderService.deleteById(orderId);
            throw new ServiceUnavailableException("Достигнут лимит одновременных асинхронных поисков реквизитов, " +
                    "ордер " + orderId + " удалён.", e);
        }
    }

    private void searchDetails(CreateOrderCommand command, UUID orderId, int orderTimeoutSeconds,
                               boolean notifyClient) {
        detailsSearchService.search(command.clientId(), orderId, command.request(), command.timeoutSeconds(),
                command.testOrder()).ifPresentOrElse(
                detailsResponse -> completeSearch(orderId, detailsResponse, orderTimeoutSeconds, notifyClient),
                () -> failSearch(orderId, notifyClient)
        );
    }

    private ClientOrderSummary findSummary(UUID orderId) {
        return orderService.findById(orderId, ClientOrderSummary.class)
                .orElseThrow(() -> new BaseException("Ордер не найден по идентификатору " + orderId));
    }

    private void completeSearch(UUID orderId, ApiDetailsResponse detailsResponse, int orderTimeoutSeconds,
                                boolean notifyClient) {
        OrderStatusChange change = orderFacade.routeOrder(OrderStatus.NEW, orderId, OrderStatusChangeSource.SYSTEM,
                notifyClient, order -> fillDetails(order, detailsResponse, orderTimeoutSeconds));
        if (!OrderStatusTransition.ALLOWED.equals(change.transition())) {
            log.info("Реквизиты по ордеру {} найдены, но ордер уже в статусе {}.", orderId, change.from());
            return;
        }
        processMerchantCallbacks(orderId);
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

    private void failSearch(UUID orderId, boolean notifyClient) {
        orderFacade.routeOrder(OrderStatus.DETAILS_NOT_FOUND, orderId, OrderStatusChangeSource.SYSTEM, notifyClient,
                order -> { });
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
