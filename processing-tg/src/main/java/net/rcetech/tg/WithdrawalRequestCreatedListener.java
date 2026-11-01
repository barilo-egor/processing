package net.rcetech.tg;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.meta.billing.WithdrawalRequestCreatedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class WithdrawalRequestCreatedListener {

    private static final String WITHDRAWALS_PATH = "/support/dashboard?section=withdrawals";

    private final MethodExecutor methodExecutor;

    private final Long chatId;

    private final String withdrawalsUrl;

    public WithdrawalRequestCreatedListener(MethodExecutor methodExecutor,
                                            @Value("${tg.bot.group-chat-id}") Long chatId,
                                            @Value("${processing.uri.base-url}") String baseUrl) {
        this.methodExecutor = methodExecutor;
        this.chatId = chatId;
        this.withdrawalsUrl = baseUrl + WITHDRAWALS_PATH;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void withdrawalRequestCreated(WithdrawalRequestCreatedEvent event) {
        try {
            methodExecutor.sendMessage(chatId, "Клиент " + event.username() + " создал заявку на вывод на сумму "
                    + event.amount() + ".\n" + withdrawalsUrl);
        } catch (Exception e) {
            log.error("Ошибка отправки уведомления о создании заявки на вывод {}: {}", event.id(), e.getMessage(), e);
        }
    }
}
