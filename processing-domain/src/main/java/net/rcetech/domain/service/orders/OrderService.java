package net.rcetech.domain.service.orders;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.exception.OrderStatusUpdateException;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusTransition;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@Transactional
public class OrderService {

    private static final String ORDER_NOT_FOUND_MESSAGE = "Order not found";

    private final OrderRepository orderRepository;

    private final ApplicationEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    public Order save(Order order) {
        return orderRepository.save(order);
    }

    public Optional<Order> findById(UUID id) {
        return orderRepository.findById(id);
    }

    public <T> Optional<T> findById(UUID id, Class<T> projectionType) {
        Order order = new Order();
        order.setId(id);
        return orderRepository.findBy(Example.of(order), query -> query.as(projectionType).one());
    }

    public Optional<Order> findByMerchantOrderId(String merchantOrderId) {
        return orderRepository.findByMerchantOrderId(merchantOrderId);
    }

    public <T> Page<T> findAll(PredicateSpecification<Order> filter, Pageable pageable, Class<T> projectionType) {
        return orderRepository.findBy(filter,
                query -> query.as(projectionType).page(pageable));
    }

    public List<Order> findAllExpired() {
        return orderRepository.findAllByStatusAndExpiresAtBefore(OrderStatus.NEW, Instant.now());
    }

    @Transactional
    public void updateStatus(UUID id, OrderStatus statusTo, String merchantOrderStatus) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new BaseException(ORDER_NOT_FOUND_MESSAGE));
        OrderStatus statusFrom = order.getStatus();
        switch (OrderStatusTransition.of(statusFrom, statusTo)) {
            case ALLOWED -> {
                order.setStatus(statusTo);
                if (Objects.nonNull(merchantOrderStatus)) {
                    order.setMerchantOrderStatus(merchantOrderStatus);
                }
                eventPublisher.publishEvent(new OrderStatusUpdatedEvent(order.getId(), order.getStatus()));
            }
            case SKIPPED -> log.info("Переход ордера {} из статуса {} в статус {} пропущен.",
                    order.getId(), statusFrom, statusTo);
            case REJECTED -> throw new OrderStatusUpdateException(
                    "Переход ордера из статуса «" + statusFrom.getDescription() + "» в статус «"
                            + statusTo.getDescription() + "» запрещён.",
                    statusFrom, statusTo);
        }
    }

    public boolean existsById(UUID id) {
        return orderRepository.existsById(id);
    }

    public boolean existsByInternalId(String internalId) {
        return orderRepository.existsByInternalId(internalId);
    }
}
