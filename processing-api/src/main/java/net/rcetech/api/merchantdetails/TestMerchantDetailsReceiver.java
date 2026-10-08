package net.rcetech.api.merchantdetails;

import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.meta.Profiles;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Profile(Profiles.TEST_MERCHANT_DETAILS)
public class TestMerchantDetailsReceiver implements MerchantDetailsReceiver {

    private final ApiMerchantDetailsGrpcService apiMerchantDetailsGrpcService;

    public TestMerchantDetailsReceiver(ApiMerchantDetailsGrpcService apiMerchantDetailsGrpcService) {
        this.apiMerchantDetailsGrpcService = apiMerchantDetailsGrpcService;
    }

    @Override
    public ApiDetailsResponse getDetails(UUID clientId, UUID orderId, CreateOrderRequest clientOrderRequest,
                                         Integer timeoutSeconds) {
        return apiMerchantDetailsGrpcService.getTestDetails(clientId, orderId, clientOrderRequest);
    }
}
