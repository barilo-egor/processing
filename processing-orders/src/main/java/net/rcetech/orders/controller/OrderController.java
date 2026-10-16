package net.rcetech.orders.controller;

import jakarta.validation.Valid;
import net.rcetech.domain.repository.orders.OrderSpecifications;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.orders.dto.OrderFilter;
import net.rcetech.meta.orders.dto.OrderResponse;
import net.rcetech.meta.orders.dto.OrderSummary;
import net.rcetech.meta.orders.dto.OrderUpdateRequest;
import net.rcetech.orders.OrderFacade;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(WebPath.PRIVATE_API_PATH + "/order")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class OrderController {

    private final OrderService orderService;

    private final OrderFacade orderFacade;

    public OrderController(OrderService orderService, OrderFacade orderFacade) {
        this.orderService = orderService;
        this.orderFacade = orderFacade;
    }

    @GetMapping
    public PagedModel<OrderSummary> getOrders(OrderFilter filter, @PageableDefault(size = 20) Pageable pageable){
        return new PagedModel<>(orderService.findAll(OrderSpecifications.matches(filter), pageable, OrderSummary.class));
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable UUID id) {
        return orderService.findById(id, OrderResponse.class)
                .orElseThrow(() -> new BadRequestException("Order not found"));
    }

    @PatchMapping("/{id}")
    public void update(@PathVariable UUID id, @Valid @RequestBody OrderUpdateRequest request) {
        if (!orderService.existsById(id)) {
            throw new BadRequestException("Ордер не найден.");
        }
        orderFacade.routeOrder(request.status(), id, null);
    }
}
