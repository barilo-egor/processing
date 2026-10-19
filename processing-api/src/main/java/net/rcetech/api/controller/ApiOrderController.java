package net.rcetech.api.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.api.service.OrderApiService;
import net.rcetech.domain.mapping.orders.OrderMapper;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.orders.dto.ClientOrderFilter;
import net.rcetech.meta.orders.dto.ClientOrderSummary;
import net.rcetech.meta.user.ClientAccess;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping(WebPath.V1_API_PATH + "/order")
@ClientAccess
public class ApiOrderController {

    private final OrderApiService orderApiService;

    private final OrderMapper orderMapper;

    public ApiOrderController(OrderApiService orderApiService, OrderMapper orderMapper) {
        this.orderApiService = orderApiService;
        this.orderMapper = orderMapper;
    }

    @PostMapping
    public ResponseEntity<ClientOrderSummary> createOrder(@Valid @RequestBody CreateOrderRequest clientRequest,
                                                          @RequestHeader(required = false, defaultValue = "30",
                                                                  value = "Search-Timeout-Seconds")
                                                                  Integer timeoutSeconds,
                                                          @RequestHeader(required = false, defaultValue = "false",
                                                                  value = "Test-Order")
                                                                  boolean testOrder,
                                                          Principal principal) {
        log.info("Клиент {} запросил через API создание ордера (internalId={}, сумма={}, методы={}, " +
                        "таймаут поиска={} с, тестовый={}).",
                principal.getName(), clientRequest.internalId(), clientRequest.amount(), clientRequest.methods(),
                timeoutSeconds, testOrder);
        Order order = orderApiService.createOrder(UUID.fromString(principal.getName()), clientRequest, timeoutSeconds,
                testOrder);
        log.info("Клиент {} создал через API ордер {} (internalId={}, сумма={}, методы={}).",
                principal.getName(), order.getId(), clientRequest.internalId(), clientRequest.amount(),
                clientRequest.methods());
        return new ResponseEntity<>(orderMapper.toOrderSummary(order), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientOrderSummary> getOrder(@PathVariable UUID id, Principal principal) {
        return new ResponseEntity<>(orderMapper.toOrderSummary(
                orderApiService.findByIdAndClientId(UUID.fromString(principal.getName()), id)),
                HttpStatus.OK
        );
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public void cancelOrder(@PathVariable UUID id, Principal principal) {
        orderApiService.cancelOrder(UUID.fromString(principal.getName()), id);
        log.info("Клиент {} отменил через API ордер {}.", principal.getName(), id);
    }

    @GetMapping
    public PagedModel<ClientOrderSummary> getOrders(ClientOrderFilter filter,
                                                    @PageableDefault(size = 20) Pageable pageable,
                                                    Principal principal) {
        return new PagedModel<>(orderApiService.findAll(UUID.fromString(principal.getName()), filter, pageable));
    }
}
