package net.rcetech.orders.mapper;

import com.google.protobuf.Timestamp;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import tgb.cryptoexchange.commons.enums.Merchant;
import tgb.cryptoexchange.grpc.generated.CreateOrderGrpc;
import tgb.cryptoexchange.grpc.generated.CreateOrderResponseGrpc;
import tgb.cryptoexchange.grpc.generated.GetOrdersGrpc;
import tgb.cryptoexchange.grpc.generated.OrderResponse;
import net.rcetech.orders.dto.OrderDTO;
import net.rcetech.orders.entity.Order;
import net.rcetech.orders.enums.OrderStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class OrderMapper {

    public OrderDTO toDTO(CreateOrderGrpc order) {
        return OrderDTO.builder()
                .id(UUID.fromString(order.getId()))
                .clientId(order.getClientId())
                .internalId(order.getInternalId())
                .merchant(Merchant.valueOf(order.getMerchant()))
                .merchantOrderId(order.getMerchantOrderId())
                .merchantOrderStatus(order.getMerchantOrderStatus())
                .amount(order.getAmount())
                .enableUniqueAmount(order.getEnableUniqueAmount())
                .callbackUrl(order.hasCallbackUrl() ? order.getCallbackUrl() : null)
                .build();
    }

    public OrderDTO entityToDTO(Order order) {
        return OrderDTO.builder()
                .id(order.getId())
                .clientId(order.getClientId())
                .internalId(order.getInternalId())
                .status(order.getStatus())
                .amount(order.getAmount())
                .enableUniqueAmount(order.getEnableUniqueAmount())
                .callbackUrl(order.getCallbackUrl())
                .createdAt(order.getCreatedAt())
                .build();
    }

    public CreateOrderResponseGrpc createOrderResponseGrpc(OrderDTO orderDTO) {
        CreateOrderResponseGrpc.Builder builder = CreateOrderResponseGrpc.newBuilder()
                .setId(Objects.nonNull(orderDTO.getId()) ? orderDTO.getId().toString() : StringUtils.EMPTY)
                .setClientId(Objects.nonNull(orderDTO.getClientId()) ? orderDTO.getClientId() : 0)
                .setInternalId(Objects.requireNonNullElse(orderDTO.getInternalId(), StringUtils.EMPTY))
                .setStatus(Objects.nonNull(orderDTO.getStatus()) ? orderDTO.getStatus().name() : StringUtils.EMPTY)
                .setAmount(Objects.nonNull(orderDTO.getAmount()) ? orderDTO.getAmount() : 0)
                .setEnableUniqueAmount(
                        Objects.nonNull(orderDTO.getEnableUniqueAmount()) && orderDTO.getEnableUniqueAmount())
                .setCallbackUrl(Objects.requireNonNullElse(orderDTO.getCallbackUrl(), StringUtils.EMPTY));
        if (Objects.nonNull(orderDTO.getCreatedAt())) {
            builder.setCreatedAt(instantToTimestamp(orderDTO.getCreatedAt()));
        }
        return builder.build();
    }

    public Specification<Order> buildFindSpecification(GetOrdersGrpc request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            addIdentifiersPredicates(request, root, criteriaBuilder, predicates);
            addAmountPredicates(request, root, criteriaBuilder, predicates);
            addDatePredicates(request, root, criteriaBuilder, predicates);
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void addIdentifiersPredicates(GetOrdersGrpc request, Root<Order> root, CriteriaBuilder cb,
            List<Predicate> predicates) {
        if (request.hasId()) {
            predicates.add(cb.equal(root.get("id"), UUID.fromString(request.getId())));
        }
        if (!CollectionUtils.isEmpty(request.getClientIdsList())) {
            predicates.add(root.get("clientId").in(request.getClientIdsList()));
        }
        if (request.hasInternalId()) {
            predicates.add(cb.equal(root.get("internalId"), request.getInternalId()));
        }
        if (!CollectionUtils.isEmpty(request.getStatusesList())) {
            List<OrderStatus> statuses = request.getStatusesList().stream().map(OrderStatus::valueOf).toList();
            predicates.add(root.get("status").in(statuses));
        }
    }

    private void addAmountPredicates(GetOrdersGrpc request, Root<Order> root, CriteriaBuilder cb,
            List<Predicate> predicates) {
        if (request.hasMinAmount()) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), request.getMinAmount()));
        }
        if (request.hasMaxAmount()) {
            predicates.add(cb.lessThanOrEqualTo(root.get("amount"), request.getMaxAmount()));
        }
    }

    private void addDatePredicates(GetOrdersGrpc request, Root<Order> root, CriteriaBuilder cb,
            List<Predicate> predicates) {
        if (request.hasCreatedAtFrom()) {
            Instant from = Instant.ofEpochSecond(request.getCreatedAtFrom().getSeconds(),
                    request.getCreatedAtFrom().getNanos());
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (request.hasCreatedAtTo()) {
            Instant to = Instant.ofEpochSecond(request.getCreatedAtTo().getSeconds(),
                    request.getCreatedAtTo().getNanos());
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
    }

    public OrderResponse toOrderResponse(OrderDTO orderDTO) {
        OrderResponse.Builder builder = OrderResponse.newBuilder()
                .setId(orderDTO.getId().toString())
                .setClientId(orderDTO.getClientId())
                .setInternalId(orderDTO.getInternalId())
                .setStatus(orderDTO.getStatus().name())
                .setAmount(orderDTO.getAmount())
                .setEnableUniqueAmount(orderDTO.getEnableUniqueAmount())
                .setCreatedAt(instantToTimestamp(orderDTO.getCreatedAt()));
        if (StringUtils.isNotBlank(orderDTO.getCallbackUrl())) {
            builder.setCallbackUrl(orderDTO.getCallbackUrl());
        }
        return builder.build();
    }

    private Timestamp instantToTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

}
