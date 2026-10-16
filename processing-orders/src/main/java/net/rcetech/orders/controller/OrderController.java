package net.rcetech.orders.controller;

import jakarta.validation.Valid;
import net.rcetech.domain.repository.orders.OrderSpecifications;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.exception.OrderStatusUpdateException;
import net.rcetech.meta.orders.OrderStatusChange;
import net.rcetech.meta.orders.OrderStatusTransition;
import net.rcetech.meta.orders.dto.OrderFilter;
import net.rcetech.meta.orders.dto.OrderResponse;
import net.rcetech.meta.orders.dto.OrderSummary;
import net.rcetech.meta.orders.dto.OrderUpdateRequest;
import net.rcetech.meta.user.SupportAccess;
import net.rcetech.orders.OrderFacade;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(WebPath.SUPPORT_API_PATH + "/order")
@SupportAccess
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
        OrderStatusChange change = orderFacade.routeOrder(request.status(), id, null);
        if (OrderStatusTransition.SKIPPED.equals(change.transition())) {
            String message = change.from().equals(change.to())
                    ? "Ордер уже в статусе «" + change.to().getDescription() + "»."
                    : "Переход ордера из статуса «" + change.from().getDescription() + "» в статус «"
                    + change.to().getDescription() + "» не выполняется.";
            throw new OrderStatusUpdateException(message, change.from(), change.to());
        }
    }
}
