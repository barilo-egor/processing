package net.rcetech.tg;

import net.rcetech.meta.exception.BaseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MethodExecutorTest {

    @Mock
    private TelegramClient telegramClient;

    @InjectMocks
    private MethodExecutor methodExecutor;

    @ParameterizedTest
    @CsvSource({"123456789,Привет", "-1001234567890,Новый ордер"})
    @DisplayName("sendMessage должен отправлять сообщение с переданными чатом и текстом.")
    void sendMessage_shouldExecuteSendMessage(Long chatId, String text) throws TelegramApiException {
        methodExecutor.sendMessage(chatId, text);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertAll(
                () -> assertEquals(chatId.toString(), captor.getValue().getChatId()),
                () -> assertEquals(text, captor.getValue().getText())
        );
    }

    @ParameterizedTest
    @CsvSource({"123456789,Привет,Bad Request", "-1001234567890,Новый ордер,Forbidden"})
    @DisplayName("sendMessage должен оборачивать ошибку Telegram API в BaseException.")
    void sendMessage_shouldWrapTelegramApiException(Long chatId, String text, String error)
            throws TelegramApiException {
        TelegramApiException cause = new TelegramApiException(error);
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(cause);

        BaseException exception = assertThrows(BaseException.class, () -> methodExecutor.sendMessage(chatId, text));
        assertSame(cause, exception.getCause());
    }
}
