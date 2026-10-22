package net.rcetech.api.service;

import net.rcetech.api.dto.CreateOrderCommand;
import net.rcetech.api.merchantdetails.ApiDetailsResponse;
import net.rcetech.api.merchantdetails.MerchantDetailsReceiver;
import net.rcetech.api.merchantdetails.RandomDetailsReceiver;
import net.rcetech.domain.model.orders.Order;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class DetailsSearchService {

    private final MerchantDetailsReceiver merchantDetailsReceiver;

    private final RandomDetailsReceiver randomDetailsReceiver;

    private final Executor virtualTaskExecutor;

    public DetailsSearchService(MerchantDetailsReceiver merchantDetailsReceiver,
                                RandomDetailsReceiver randomDetailsReceiver,
                                @Qualifier("virtualTaskExecutor") Executor virtualTaskExecutor) {
        this.merchantDetailsReceiver = merchantDetailsReceiver;
        this.randomDetailsReceiver = randomDetailsReceiver;
        this.virtualTaskExecutor = virtualTaskExecutor;
    }

    public Optional<ApiDetailsResponse> search(Order order, CreateOrderCommand command) {
        MerchantDetailsReceiver receiver = command.testOrder() ? randomDetailsReceiver : merchantDetailsReceiver;
        return receiver.getDetails(order.getClient().getId(), order.getId(), command.request(),
                command.timeoutSeconds());
    }

    public CompletableFuture<Optional<ApiDetailsResponse>> searchAsync(Order order, CreateOrderCommand command) {
        return CompletableFuture.supplyAsync(() -> search(order, command), virtualTaskExecutor);
    }
}
