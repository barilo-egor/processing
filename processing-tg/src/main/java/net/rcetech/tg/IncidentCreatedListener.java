package net.rcetech.tg;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.meta.incidents.IncidentCreatedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class IncidentCreatedListener {

    private static final String INCIDENTS_PATH = "/support/dashboard?section=incidents";

    private final MethodExecutor methodExecutor;

    private final Long chatId;

    private final String incidentsUrl;

    public IncidentCreatedListener(MethodExecutor methodExecutor,
                                   @Value("${tg.bot.incidents-chat-id}") Long chatId,
                                   @Value("${processing.uri.base-url}") String baseUrl) {
        this.methodExecutor = methodExecutor;
        this.chatId = chatId;
        this.incidentsUrl = baseUrl + INCIDENTS_PATH;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void incidentCreated(IncidentCreatedEvent event) {
        try {
            methodExecutor.sendMessage(chatId, "Создан инцидент №" + event.id() + ": "
                    + event.type().getSummary() + ".\n" + incidentsUrl);
        } catch (Exception e) {
            log.error("Ошибка отправки уведомления о создании инцидента {}: {}", event.id(), e.getMessage(), e);
        }
    }
}
