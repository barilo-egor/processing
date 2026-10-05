package net.rcetech.api.merchantdetails;

import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.meta.Profiles;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Profile(Profiles.NOT_TEST_MERCHANT_DETAILS)
public class MerchantDetailsReceiverImpl implements MerchantDetailsReceiver {

    private final ApiMerchantDetailsGrpcService apiMerchantDetailsGrpcService;

    public MerchantDetailsReceiverImpl(ApiMerchantDetailsGrpcService apiMerchantDetailsGrpcService) {
        this.apiMerchantDetailsGrpcService = apiMerchantDetailsGrpcService;
    }

    @Override
    public ApiDetailsResponse getDetails(UUID clientId, UUID orderId, CreateOrderRequest clientOrderRequest) {
        return apiMerchantDetailsGrpcService.getDetails(clientId, orderId, clientOrderRequest);
    }
}
