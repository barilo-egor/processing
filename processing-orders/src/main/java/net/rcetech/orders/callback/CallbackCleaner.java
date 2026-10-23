package net.rcetech.orders.callback;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import net.rcetech.domain.service.orders.ClientCallbackService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CallbackCleaner {

    private static final Duration RETENTION = Duration.ofDays(7);

    private final ClientCallbackService clientCallbackService;

    public CallbackCleaner(ClientCallbackService clientCallbackService) {
        this.clientCallbackService = clientCallbackService;
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    @SchedulerLock(name = "CallbackCleaner.deleteOld", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void deleteOld() {
        int deleted = clientCallbackService.deleteFinishedBefore(Instant.now().minus(RETENTION));
        if (deleted > 0) {
            log.info("Удалено {} завершённых КБ клиентам старше {} дней.", deleted, RETENTION.toDays());
        }
    }
}
