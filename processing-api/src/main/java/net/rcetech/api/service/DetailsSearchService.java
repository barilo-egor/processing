package net.rcetech.api.service;

import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.api.merchantdetails.ApiDetailsResponse;
import net.rcetech.api.merchantdetails.MerchantDetailsReceiver;
import net.rcetech.api.merchantdetails.RandomDetailsReceiver;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class DetailsSearchService {

    private final MerchantDetailsReceiver merchantDetailsReceiver;

    private final RandomDetailsReceiver randomDetailsReceiver;

    public DetailsSearchService(MerchantDetailsReceiver merchantDetailsReceiver,
                                RandomDetailsReceiver randomDetailsReceiver) {
        this.merchantDetailsReceiver = merchantDetailsReceiver;
        this.randomDetailsReceiver = randomDetailsReceiver;
    }

    public Optional<ApiDetailsResponse> search(UUID clientId, UUID orderId, CreateOrderRequest createOrderRequest,
                                               Integer timeoutSeconds, boolean testOrder) {
        MerchantDetailsReceiver receiver = testOrder ? randomDetailsReceiver : merchantDetailsReceiver;
        return receiver.getDetails(clientId, orderId, createOrderRequest, timeoutSeconds);
    }
}
