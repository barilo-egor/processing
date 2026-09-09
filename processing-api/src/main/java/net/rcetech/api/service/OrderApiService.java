package net.rcetech.api.service;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.api.dto.ApiDetailsResponse;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.orders.OrderSpecifications;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.dto.ClientOrderFilter;
import net.rcetech.meta.orders.dto.OrderSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class OrderApiService {

    private final ApiMerchantDetailsGrpcService detailsGrpcService;

    private final ClientService clientService;

    private final OrderService orderService;

    public OrderApiService(ApiMerchantDetailsGrpcService detailsGrpcService,
                           ClientService clientService, OrderService orderService) {
        this.detailsGrpcService = detailsGrpcService;
        this.clientService = clientService;
        this.orderService = orderService;
    }

    public Order createOrder(UUID clientId, CreateOrderRequest createOrderRequest) {
        Client client = clientService.findById(clientId)
                .orElseThrow(() -> new BaseException("Клиент не найден по идентификатору " + clientId));
        UUID orderId = UUID.randomUUID();
        ApiDetailsResponse detailsResponse = detailsGrpcService.getDetails(orderId, createOrderRequest);
        ApiDetailsResponse.Details details = detailsResponse.details();
        Order order = new Order();
        order.setId(orderId);
        order.setCreatedAt(Instant.now());
        order.setExpiresAt(order.getCreatedAt().plusSeconds(client.getOrderTimeoutSeconds()));
        order.setClient(client);
        order.setInternalId(createOrderRequest.internalId());
        order.setStatus(OrderStatus.NEW);
        order.setAmount(
                Objects.nonNull(detailsResponse.amount())
                        ? detailsResponse.amount()
                        : createOrderRequest.amount()
        );
        order.setEnableUniqueAmount(createOrderRequest.enableUniqueAmount());
        order.setMerchant(detailsResponse.merchant());
        order.setMerchantOrderId(detailsResponse.orderId());
        order.setMerchantOrderStatus(detailsResponse.orderStatus());
        order.setMethod(details.requestMethod());
        order.setDetails(details.details());
        order.setBank(details.bank());
        order.setCallbackUrl(
                Objects.isNull(createOrderRequest.callbackUrl())
                        ? client.getCallbackUrl()
                        : createOrderRequest.callbackUrl()
        );
        return orderService.save(order);
    }

    public Order findByIdAndClientId(UUID clientId, UUID id) {
        Optional<Order> maybeOrder = orderService.findById(id);
        if (maybeOrder.isEmpty() || !clientId.equals(maybeOrder.get().getClient().getId())) {
            throw new BadRequestException("Order not found");
        }
        return maybeOrder.get();
    }

    @Transactional
    public void cancelOrder(UUID clientId, UUID id) {
        Order order = findByIdAndClientId(clientId, id);
        order.setStatus(OrderStatus.CANCELED);
        orderService.save(order);
    }

    public Page<OrderSummary> findAll(UUID clientId, ClientOrderFilter filter, Pageable pageable) {
        return orderService.findAll(OrderSpecifications.matches(clientId, filter), pageable);
    }

}
