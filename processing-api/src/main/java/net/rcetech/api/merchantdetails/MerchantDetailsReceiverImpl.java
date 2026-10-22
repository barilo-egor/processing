package net.rcetech.api.merchantdetails;

import net.rcetech.api.dto.CreateOrderRequest;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@Primary
public class MerchantDetailsReceiverImpl implements MerchantDetailsReceiver {

    private final ApiMerchantDetailsGrpcService apiMerchantDetailsGrpcService;

    public MerchantDetailsReceiverImpl(ApiMerchantDetailsGrpcService apiMerchantDetailsGrpcService) {
        this.apiMerchantDetailsGrpcService = apiMerchantDetailsGrpcService;
    }

    @Override
    public Optional<ApiDetailsResponse> getDetails(UUID clientId, UUID orderId, CreateOrderRequest clientOrderRequest,
                                         Integer timeoutSeconds) {
        return apiMerchantDetailsGrpcService.getDetails(clientId, orderId, clientOrderRequest, timeoutSeconds);
    }
}
