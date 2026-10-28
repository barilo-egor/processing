package net.rcetech.tg;

import net.rcetech.meta.exception.BaseException;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Service
public class MethodExecutor {

    private static final int MAX_MESSAGE_LENGTH = 4096;

    private final TelegramClient telegramClient;

    public MethodExecutor(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public void sendMessage(Long chatId, String text) {
        int start = 0;
        do {
            int end = Math.min(start + MAX_MESSAGE_LENGTH, text.length());
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
                end--;
            }
            execute(chatId, text.substring(start, end));
            start = end;
        } while (start < text.length());
    }

    private void execute(Long chatId, String text) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new BaseException("Не удалось отправить сообщение в чат " + chatId + ": " + e.getMessage(), e);
        }
    }
}
