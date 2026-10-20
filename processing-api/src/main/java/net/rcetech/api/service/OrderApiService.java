package net.rcetech.api.service;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.api.merchantdetails.ApiDetailsResponse;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.orders.OrderSpecifications;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.exception.ExceptionMessage;
import net.rcetech.meta.exception.OrderStatusUpdateException;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChange;
import net.rcetech.meta.orders.OrderStatusChangeSource;
import net.rcetech.meta.orders.OrderStatusTransition;
import net.rcetech.meta.orders.dto.ClientOrderFilter;
import net.rcetech.meta.orders.dto.ClientOrderSummary;
import net.rcetech.orders.OrderFacade;
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

    public OrderApiService(DetailsSearchService detailsSearchService, ClientService clientService,
                           OrderService orderService, OrderFacade orderFacade) {
        this.detailsSearchService = detailsSearchService;
        this.clientService = clientService;
        this.orderService = orderService;
        this.orderFacade = orderFacade;
    }

    public ClientOrderSummary createOrder(UUID clientId, CreateOrderRequest createOrderRequest,
                                          Integer timeoutSeconds, boolean testOrder) {
        Client client = clientService.findById(clientId)
                .orElseThrow(() -> new BaseException("Клиент не найден по идентификатору " + clientId));
        if (orderService.existsByInternalId(createOrderRequest.internalId())) {
            throw new BadRequestException(ExceptionMessage.INTERNAL_ID_NOT_UNIQUE);
        }
        Order order = orderService.createOrder(
                client,
                createOrderRequest.internalId(),
                createOrderRequest.amount(),
                createOrderRequest.callbackUrl()
        );
        detailsSearchService.search(order, createOrderRequest, timeoutSeconds, testOrder).ifPresentOrElse(
                detailsResponse -> completeSearch(
                        order.getId(),
                        detailsResponse,
                        client.getOrderTimeoutSeconds()
                ),
                () -> failSearch(order.getId())
        );
        return orderService.findById(order.getId(), ClientOrderSummary.class)
                .orElseThrow(() -> new BaseException("Ордер не найден по идентификатору " + order.getId()));
    }

    private void completeSearch(UUID orderId, ApiDetailsResponse detailsResponse, int orderTimeoutSeconds) {
        OrderStatusChange change = orderFacade.routeOrder(OrderStatus.NEW, orderId, OrderStatusChangeSource.SYSTEM,
                false, order -> fillDetails(order, detailsResponse, orderTimeoutSeconds));
        if (!OrderStatusTransition.ALLOWED.equals(change.transition())) {
            log.info("Реквизиты по ордеру {} найдены, но ордер уже в статусе {}.", orderId, change.from());
        }
    }

    private void failSearch(UUID orderId) {
        orderFacade.routeOrder(OrderStatus.DETAILS_NOT_FOUND, orderId, OrderStatusChangeSource.SYSTEM, false,
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
