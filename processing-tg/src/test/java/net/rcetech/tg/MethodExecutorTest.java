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

import java.util.List;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
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
    @CsvSource({"123456789,4097,2", "-1001234567890,10000,3", "123456789,8192,2"})
    @DisplayName("sendMessage должен разбивать текст длиннее 4096 символов на несколько сообщений по порядку.")
    void sendMessage_shouldSplitLongText(Long chatId, int length, int expectedCount) throws TelegramApiException {
        String text = IntStream.range(0, length).mapToObj(i -> String.valueOf((char) ('a' + i % 26)))
                .collect(Collectors.joining());

        methodExecutor.sendMessage(chatId, text);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, times(expectedCount)).execute(captor.capture());
        List<SendMessage> messages = captor.getAllValues();
        assertAll(
                () -> assertTrue(messages.stream().allMatch(message -> message.getText().length() <= 4096)),
                () -> assertTrue(messages.stream().allMatch(message -> chatId.toString().equals(message.getChatId()))),
                () -> assertEquals(text, messages.stream().map(SendMessage::getText).collect(Collectors.joining()))
        );
    }

    @ParameterizedTest
    @CsvSource({"123456789,4095", "-1001234567890,8191"})
    @DisplayName("sendMessage не должен разрывать суррогатную пару на границе сообщений.")
    void sendMessage_shouldNotSplitSurrogatePair(Long chatId, int prefixLength) throws TelegramApiException {
        String text = "a".repeat(prefixLength) + "😀" + "b".repeat(10);

        methodExecutor.sendMessage(chatId, text);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeast(2)).execute(captor.capture());
        List<SendMessage> messages = captor.getAllValues();
        assertAll(
                () -> assertTrue(messages.stream().noneMatch(message ->
                        Character.isHighSurrogate(message.getText().charAt(message.getText().length() - 1)))),
                () -> assertEquals(text, messages.stream().map(SendMessage::getText).collect(Collectors.joining()))
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
