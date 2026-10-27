package net.rcetech.tg;

import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChangeSource;
import net.rcetech.meta.orders.OrderStatusUpdatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderDisputeListenerTest {

    @Mock
    private MethodExecutor methodExecutor;

    @ParameterizedTest
    @CsvSource({
            "-1001234567890,https://processing.example.com,5f0c7a52-3b1e-4d8a-9c6f-1a2b3c4d5e6f,true",
            "123456789,http://localhost:8081,9e8d7c6b-5a49-4382-a1b0-c9d8e7f6a5b4,false"
    })
    @DisplayName("Слушатель должен отправить в чат номер ордера и ссылку на раздел ордеров, если мерчант открыл спор.")
    void orderStatusUpdated_shouldSendMessage(Long chatId, String baseUrl, UUID orderId, boolean notifyClient) {
        OrderDisputeListener listener = new OrderDisputeListener(methodExecutor, chatId, baseUrl);

        listener.orderStatusUpdated(new OrderStatusUpdatedEvent(orderId, OrderStatus.DISPUTE,
                OrderStatusChangeSource.MERCHANT_CALLBACK, notifyClient));

        verify(methodExecutor).sendMessage(chatId, "Мерчант открыл спор по ордеру " + orderId + ".\n"
                + baseUrl + "/support/dashboard?section=orders");
    }

    @ParameterizedTest
    @CsvSource({
            "DISPUTE,SUPPORT",
            "DISPUTE,CLIENT",
            "DISPUTE,SYSTEM",
            "SUCCESS,MERCHANT_CALLBACK",
            "CANCELED,MERCHANT_CALLBACK",
            "NEW,SUPPORT"
    })
    @DisplayName("Слушатель не должен отправлять сообщение, если это не открытие спора по КБ мерчанта.")
    void orderStatusUpdated_shouldNotSendIfNotMerchantDispute(OrderStatus status, OrderStatusChangeSource source) {
        OrderDisputeListener listener = new OrderDisputeListener(methodExecutor, 123456789L,
                "https://processing.example.com");

        listener.orderStatusUpdated(new OrderStatusUpdatedEvent(UUID.randomUUID(), status, source, true));

        verifyNoInteractions(methodExecutor);
    }

    @ParameterizedTest
    @CsvSource({"5f0c7a52-3b1e-4d8a-9c6f-1a2b3c4d5e6f", "9e8d7c6b-5a49-4382-a1b0-c9d8e7f6a5b4"})
    @DisplayName("Слушатель не должен бросать исключение, если сообщение не удалось отправить.")
    void orderStatusUpdated_shouldNotThrowIfSendFailed(UUID orderId) {
        OrderDisputeListener listener = new OrderDisputeListener(methodExecutor, 123456789L,
                "https://processing.example.com");
        doThrow(new BaseException("Не удалось отправить сообщение")).when(methodExecutor)
                .sendMessage(anyLong(), anyString());

        assertDoesNotThrow(() -> listener.orderStatusUpdated(new OrderStatusUpdatedEvent(orderId,
                OrderStatus.DISPUTE, OrderStatusChangeSource.MERCHANT_CALLBACK, true)));
    }
}
