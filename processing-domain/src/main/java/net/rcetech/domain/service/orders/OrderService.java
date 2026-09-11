package net.rcetech.domain.service.orders;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.orders.OrderRepository;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
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

    public <T> Page<T> findAll(PredicateSpecification<Order> filter, Pageable pageable, Class<T> projectionType) {
        return orderRepository.findBy(filter,
                query -> query.as(projectionType).page(pageable));
    }
}
