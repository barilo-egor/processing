package net.rcetech.tg;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.meta.incidents.IncidentSaveErrorEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class IncidentSaveErrorListener {

    private final MethodExecutor methodExecutor;

    private final Long chatId;

    public IncidentSaveErrorListener(MethodExecutor methodExecutor,
                                     @Value("${tg.bot.group-chat-id}") Long chatId) {
        this.methodExecutor = methodExecutor;
        this.chatId = chatId;
    }

    @EventListener
    @Async
    public void incidentSaveError(IncidentSaveErrorEvent event) {
        String text = "Не удалось сохранить инцидент " + event.type().name() + ": " + event.type().getSummary()
                + ".\nВремя ошибки: " + event.errorTimestamp()
                + "\nДанные: " + event.data();
        try {
            methodExecutor.sendMessage(chatId, text);
        } catch (Exception e) {
            log.error("Ошибка отправки уведомления об ошибке сохранения инцидента {}: {}", event.errorTimestamp(),
                    e.getMessage(), e);
        }
    }
}
