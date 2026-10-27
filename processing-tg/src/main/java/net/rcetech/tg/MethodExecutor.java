package net.rcetech.tg;

import net.rcetech.meta.exception.BaseException;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Service
public class MethodExecutor {

    private final TelegramClient telegramClient;

    public MethodExecutor(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public void sendMessage(Long chatId, String text) {
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
