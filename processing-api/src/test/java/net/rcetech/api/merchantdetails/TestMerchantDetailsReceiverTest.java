package net.rcetech.api.merchantdetails;

import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.meta.orders.RequestMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TestMerchantDetailsReceiverTest {

    @Mock
    private ApiMerchantDetailsGrpcService apiMerchantDetailsGrpcService;

    @InjectMocks
    private TestMerchantDetailsReceiver merchantDetailsReceiver;

    @ParameterizedTest
    @CsvSource({
            "b7a4d9b0-6f2a-435f-ae45-5b71c9f379d0,3b645bf9-f983-4f0a-ae35-df06fa49d2f1," +
                    "0df00d34-5673-46e9-87eb-d7e43a685ade,5023,SBP,true,https://example.com/callback," +
                    "12345",
            "80c3c49b-53ca-40a5-ad74-adb069e7882f,95180425-49ef-4376-9c70-b45caafcf803,5032262,26005,CARD," +
                    "false,https://google.com/callback,et50qwr8fs3"
    })
    @DisplayName("Метод должен передать параметры в сервис.")
    void getDetails_shouldPassParametersToService(UUID clientId, UUID orderId,
                                                  String internalId, Integer amount,
                                                  RequestMethod requestMethod, Boolean enableUniqueAmount,
                                                  String callbackUrl, String userId) {
        CreateOrderRequest request = new CreateOrderRequest(internalId, amount, Set.of(requestMethod),
                enableUniqueAmount, callbackUrl, userId);

        merchantDetailsReceiver.getDetails(clientId, orderId, request);

        verify(apiMerchantDetailsGrpcService).getDetails(clientId, orderId, request, true);
    }

}