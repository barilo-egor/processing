package net.rcetech.domain.service.orders;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.orders.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

}
