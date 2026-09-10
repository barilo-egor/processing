package net.rcetech.orders.controller;

import net.rcetech.domain.repository.orders.OrderSpecifications;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.orders.dto.OrderFilter;
import net.rcetech.meta.orders.dto.OrderResponse;
import net.rcetech.meta.orders.dto.OrderSummary;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tgb.cryptoexchange.exception.BadRequestException;

import java.util.UUID;

@RestController
@RequestMapping(WebPath.PRIVATE_API_PATH + "/order")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public PagedModel<OrderSummary> getOrders(OrderFilter filter, Pageable pageable){
        return new PagedModel<>(orderService.findAll(OrderSpecifications.matches(filter), pageable, OrderSummary.class));
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable UUID id) {
        return orderService.findById(id, OrderResponse.class)
                .orElseThrow(() -> new BadRequestException("Order not found"));
    }
}
