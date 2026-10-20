package net.rcetech.domain.service.orders;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.exception.OrderStatusUpdateException;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChange;
import net.rcetech.meta.orders.OrderStatusChangeSource;
import net.rcetech.meta.orders.OrderStatusTransition;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

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

    public Order createOrder(Client client, String internalId, Integer amount, String callbackUrl) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCreatedAt(Instant.now());
        order.setClient(client);
        order.setInternalId(internalId);
        order.setStatus(OrderStatus.SEARCHING);
        order.setAmount(amount);
        order.setCallbackUrl(Objects.isNull(callbackUrl) ? client.getCallbackUrl() : callbackUrl);
        return orderRepository.save(order);
    }

    public void deleteById(UUID id) {
        orderRepository.deleteById(id);
    }

    public Optional<Order> findById(UUID id) {
        return orderRepository.findById(id);
    }

    public <T> Optional<T> findById(UUID id, Class<T> projectionType) {
        Order order = new Order();
        order.setId(id);
        return orderRepository.findBy(Example.of(order), query -> query.as(projectionType).one());
    }

    public boolean existsByIdAndClientId(UUID id, UUID clientId) {
        return orderRepository.existsByIdAndClientId(id, clientId);
    }

    public <T> Optional<T> findByIdAndClientId(UUID id, UUID clientId, Class<T> projectionType) {
        return orderRepository.findByIdAndClientId(id, clientId, projectionType);
    }

    public Optional<Order> findByMerchantAndMerchantOrderId(Merchant merchant, String merchantOrderId) {
        return orderRepository.findByMerchantAndMerchantOrderId(merchant, merchantOrderId);
    }

    public <T> Page<T> findAll(PredicateSpecification<Order> filter, Pageable pageable, Class<T> projectionType) {
        return orderRepository.findBy(filter,
                query -> query.as(projectionType).page(pageable));
    }

    public List<Order> findAllExpired() {
        return orderRepository.findAllByStatusAndExpiresAtBefore(OrderStatus.NEW, Instant.now());
    }

    @Transactional
    public OrderStatusChange updateStatus(UUID id, OrderStatus statusTo, OrderStatusChangeSource source,
                                          String merchantOrderStatus) {
        return updateStatus(id, statusTo, source, true, order -> {
            if (Objects.nonNull(merchantOrderStatus)) {
                order.setMerchantOrderStatus(merchantOrderStatus);
            }
        });
    }

    @Transactional
    public OrderStatusChange updateStatus(UUID id, OrderStatus statusTo, OrderStatusChangeSource source,
                                          boolean notifyClient, Consumer<Order> changes) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new BaseException(ORDER_NOT_FOUND_MESSAGE));
        OrderStatus statusFrom = order.getStatus();
        OrderStatusTransition transition = resolveTransition(order, statusTo, source);
        switch (transition) {
            case ALLOWED -> {
                order.setStatus(statusTo);
                changes.accept(order);
                eventPublisher.publishEvent(
                        new OrderStatusUpdatedEvent(order.getId(), order.getStatus(), source, notifyClient)
                );
            }
            case SKIPPED -> log.info("Переход ордера {} из статуса {} в статус {} пропущен.",
                    order.getId(), statusFrom, statusTo);
            case REJECTED -> throw new OrderStatusUpdateException(
                    "Переход ордера из статуса «" + statusFrom.getDescription() + "» в статус «"
                            + statusTo.getDescription() + "» запрещён.",
                    statusFrom, statusTo);
        }
        return new OrderStatusChange(statusFrom, statusTo, transition);
    }

    private OrderStatusTransition resolveTransition(Order order, OrderStatus statusTo,
                                                    OrderStatusChangeSource source) {
        OrderStatusTransition transition = OrderStatusTransition.of(order.getStatus(), statusTo);
        if (OrderStatusTransition.ALLOWED.equals(transition) && OrderStatus.SUCCESS.equals(statusTo)
                && Objects.isNull(order.getDetails()) && !OrderStatusChangeSource.SUPPORT.equals(source)) {
            return OrderStatusTransition.REJECTED;
        }
        return transition;
    }

    public boolean existsById(UUID id) {
        return orderRepository.existsById(id);
    }

    public boolean existsByInternalId(String internalId) {
        return orderRepository.existsByInternalId(internalId);
    }
}
