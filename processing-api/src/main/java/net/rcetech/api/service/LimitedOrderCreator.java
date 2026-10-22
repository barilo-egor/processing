package net.rcetech.api.service;

import net.rcetech.api.dto.CreateOrderCommand;
import net.rcetech.meta.exception.ServiceUnavailableException;
import net.rcetech.meta.orders.dto.ClientOrderSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

@Component
public class LimitedOrderCreator {

    private final OrderApiService orderApiService;

    private final Semaphore permits;

    public LimitedOrderCreator(OrderApiService orderApiService,
                                @Value("${details-search.concurrency-limit:200}") int concurrencyLimit) {
        this.orderApiService = orderApiService;
        this.permits = new Semaphore(concurrencyLimit);
    }

    public ClientOrderSummary createOrder(CreateOrderCommand command, boolean async) {
        if (!permits.tryAcquire()) {
            throw new ServiceUnavailableException("Достигнут лимит одновременных поисков реквизитов.");
        }
        if (!async) {
            try {
                return orderApiService.createOrder(command);
            } finally {
                permits.release();
            }
        }
        try {
            return orderApiService.createOrderAsync(command, (result, e) -> permits.release());
        } catch (RuntimeException e) {
            permits.release();
            throw e;
        }
    }
}
