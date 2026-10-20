package net.rcetech.orders.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.repository.orders.OrderSpecifications;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.exception.OrderStatusUpdateException;
import net.rcetech.meta.orders.OrderStatusChange;
import net.rcetech.meta.orders.OrderStatusChangeSource;
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

import java.security.Principal;
import java.util.UUID;

@Slf4j
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
    public void update(Principal principal, @PathVariable UUID id, @Valid @RequestBody OrderUpdateRequest request) {
        if (!orderService.existsById(id)) {
            throw new BadRequestException("Ордер не найден.");
        }
        OrderStatusChange change = orderFacade.routeOrder(request.status(), id, OrderStatusChangeSource.SUPPORT, null);
        if (OrderStatusTransition.SKIPPED.equals(change.transition())) {
            throw OrderStatusUpdateException.skipped(change);
        }
        log.info("Сотрудник поддержки {} перевёл ордер {} из статуса {} в статус {}.",
                principal.getName(), id, change.from(), change.to());
    }
}
