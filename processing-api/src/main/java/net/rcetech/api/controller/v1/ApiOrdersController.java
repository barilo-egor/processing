package net.rcetech.api.controller.v1;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.api.service.OrderApiService;
import net.rcetech.domain.mapping.orders.OrderMapper;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.meta.orders.dto.OrderSummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/order")
public class ApiOrdersController {

    private final OrderApiService orderApiService;

    private final OrderMapper orderMapper;

    public ApiOrdersController(OrderApiService orderApiService, OrderMapper orderMapper) {
        this.orderApiService = orderApiService;
        this.orderMapper = orderMapper;
    }

    @PostMapping
    public ResponseEntity<OrderSummary> createOrder(@Valid @RequestBody CreateOrderRequest clientRequest,
                                                    Principal principal) {
        Order order = orderApiService.createOrder(UUID.fromString(principal.getName()), clientRequest);
        return new ResponseEntity<>(orderMapper.toOrderSummary(order), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderSummary> getOrder(@PathVariable UUID id, Principal principal) {
        return new ResponseEntity<>(orderMapper.toOrderSummary(
                orderApiService.findByIdAndClientId(UUID.fromString(principal.getName()), id)),
                HttpStatus.OK
        );
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public void cancelOrder(@PathVariable UUID id, Principal principal) {
        orderApiService.cancelOrder(UUID.fromString(principal.getName()), id);
    }
}
