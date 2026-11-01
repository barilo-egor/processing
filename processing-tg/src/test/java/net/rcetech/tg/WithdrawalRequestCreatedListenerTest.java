package net.rcetech.tg;

import net.rcetech.meta.billing.WithdrawalRequestCreatedEvent;
import net.rcetech.meta.exception.BaseException;
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

@ExtendWith(MockitoExtension.class)
class WithdrawalRequestCreatedListenerTest {

    @Mock
    private MethodExecutor methodExecutor;

    @ParameterizedTest
    @CsvSource({
            "-1001234567890,https://processing.example.com,5f0c7a52-3b1e-4d8a-9c6f-1a2b3c4d5e6f,trader_one,5000",
            "123456789,http://localhost:8081,9e8d7c6b-5a49-4382-a1b0-c9d8e7f6a5b4,ivan.petrov,125730"
    })
    @DisplayName("Слушатель должен отправить в чат логин клиента, сумму заявки и ссылку на раздел заявок на вывод.")
    void withdrawalRequestCreated_shouldSendMessage(Long chatId, String baseUrl, UUID id, String username,
                                                    Integer amount) {
        WithdrawalRequestCreatedListener listener = new WithdrawalRequestCreatedListener(methodExecutor, chatId,
                baseUrl);

        listener.withdrawalRequestCreated(new WithdrawalRequestCreatedEvent(id, username, amount));

        verify(methodExecutor).sendMessage(chatId, "Клиент " + username + " создал заявку на вывод на сумму "
                + amount + ".\n" + baseUrl + "/support/dashboard?section=withdrawals");
    }

    @ParameterizedTest
    @CsvSource({
            "5f0c7a52-3b1e-4d8a-9c6f-1a2b3c4d5e6f,trader_one,5000",
            "9e8d7c6b-5a49-4382-a1b0-c9d8e7f6a5b4,ivan.petrov,125730"
    })
    @DisplayName("Слушатель не должен бросать исключение, если сообщение не удалось отправить.")
    void withdrawalRequestCreated_shouldNotThrowIfSendFailed(UUID id, String username, Integer amount) {
        WithdrawalRequestCreatedListener listener = new WithdrawalRequestCreatedListener(methodExecutor, 123456789L,
                "https://processing.example.com");
        doThrow(new BaseException("Не удалось отправить сообщение")).when(methodExecutor)
                .sendMessage(anyLong(), anyString());

        assertDoesNotThrow(() -> listener.withdrawalRequestCreated(
                new WithdrawalRequestCreatedEvent(id, username, amount)));
    }
}
