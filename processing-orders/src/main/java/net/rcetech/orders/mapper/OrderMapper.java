package net.rcetech.orders.mapper;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import net.rcetech.orders.dto.OrderDTO;
import net.rcetech.orders.entity.Order;
import net.rcetech.orders.enums.OrderStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import rce.tech.ordersapi.dto.CreateOrderRequestDTO;
import rce.tech.ordersapi.dto.GetOrdersFilterDTO;
import rce.tech.ordersapi.dto.OrderResponseDTO;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class OrderMapper {

    public OrderDTO toDTO(CreateOrderRequestDTO request) {
        return OrderDTO.builder()
                .id(request.id())
                .clientId(request.clientId())
                .internalId(request.internalId())
                .merchant(Merchant.valueOf(request.merchant()))
                .merchantOrderId(request.merchantOrderId())
                .merchantOrderStatus(request.merchantOrderStatus())
                .amount(request.amount())
                .enableUniqueAmount(request.enableUniqueAmount())
                .callbackUrl(request.callbackUrl())
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

    public OrderResponseDTO toOrderResponseDTO(OrderDTO orderDTO) {
        return new OrderResponseDTO(
                orderDTO.getId(),
                orderDTO.getClientId(),
                orderDTO.getInternalId(),
                Objects.nonNull(orderDTO.getStatus()) ? orderDTO.getStatus().name() : null,
                orderDTO.getAmount(),
                Boolean.TRUE.equals(orderDTO.getEnableUniqueAmount()),
                orderDTO.getCallbackUrl(),
                orderDTO.getCreatedAt()
        );
    }

    public Specification<Order> buildFindSpecification(GetOrdersFilterDTO filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            addIdentifiersPredicates(filter, root, criteriaBuilder, predicates);
            addAmountPredicates(filter, root, criteriaBuilder, predicates);
            addDatePredicates(filter, root, criteriaBuilder, predicates);
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void addIdentifiersPredicates(GetOrdersFilterDTO filter, Root<Order> root, CriteriaBuilder cb,
            List<Predicate> predicates) {
        if (filter.id() != null) {
            predicates.add(cb.equal(root.get("id"), filter.id()));
        }
        if (!CollectionUtils.isEmpty(filter.clientIds())) {
            predicates.add(root.get("clientId").in(filter.clientIds()));
        }
        if (filter.internalId() != null) {
            predicates.add(cb.equal(root.get("internalId"), filter.internalId()));
        }
        if (!CollectionUtils.isEmpty(filter.statuses())) {
            List<OrderStatus> statuses = filter.statuses().stream()
                    .map(OrderStatus::valueOf)
                    .toList();
            predicates.add(root.get("status").in(statuses));
        }
    }

    private void addAmountPredicates(GetOrdersFilterDTO filter, Root<Order> root, CriteriaBuilder cb,
            List<Predicate> predicates) {
        if (filter.minAmount() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.minAmount()));
        }
        if (filter.maxAmount() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.maxAmount()));
        }
    }

    private void addDatePredicates(GetOrdersFilterDTO filter, Root<Order> root, CriteriaBuilder cb,
            List<Predicate> predicates) {
        if (filter.createdAtFrom() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.createdAtFrom()));
        }
        if (filter.createdAtTo() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.createdAtTo()));
        }
    }

}
