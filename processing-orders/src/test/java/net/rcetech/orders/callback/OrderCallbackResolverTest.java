package net.rcetech.orders.callback;

import net.rcetech.domain.model.orders.Order;
import net.rcetech.meta.orders.MerchantCallbackEvent;
import net.rcetech.orders.OrderFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCallbackResolverTest {

    @Mock
    private OrderFacade orderFacade;

    @Mock
    private MerchantCallbackProcessor merchantCallbackProcessor;

    @InjectMocks
    private OrderCallbackResolver orderCallbackResolver;

    MerchantCallbackEvent event(Merchant merchant, String merchantOrderId) {
        MerchantCallbackEvent event = new MerchantCallbackEvent();
        event.setMerchant(merchant);
        event.setMerchantOrderId(merchantOrderId);
        event.setStatus("PAID");
        return event;
    }

    @ParameterizedTest
    @CsvSource({
            "ALFA_TEAM,12363466,7257beb1-b01f-4c05-a053-1e1144f3d18b",
            "DEORA,c2cbb845-7313-4106-a1a3-e9e4b328bbd9,d958fbca-5c3d-4941-8918-4c74e144d8e2"
    })
    @DisplayName("Метод должен передать найденный ордер на обработку КБ.")
    void resolve_shouldProcessFoundOrder(Merchant merchant, String merchantOrderId, UUID orderId) {
        Order order = new Order();
        order.setId(orderId);
        when(orderFacade.getOrderByIdOrMerchantOrderId(merchant, merchantOrderId)).thenReturn(Optional.of(order));

        orderCallbackResolver.resolve(event(merchant, merchantOrderId));

        verify(merchantCallbackProcessor).process(orderId);
    }

    @ParameterizedTest
    @CsvSource({
            "ALFA_TEAM,12363466",
            "DEORA,7257beb1-b01f-4c05-a053-1e1144f3d18b"
    })
    @DisplayName("Метод не должен обрабатывать КБ, если ордер не найден.")
    void resolve_shouldSkipIfOrderNotFound(Merchant merchant, String merchantOrderId) {
        when(orderFacade.getOrderByIdOrMerchantOrderId(merchant, merchantOrderId)).thenReturn(Optional.empty());

        orderCallbackResolver.resolve(event(merchant, merchantOrderId));

        verify(merchantCallbackProcessor, never()).process(any());
    }
}
