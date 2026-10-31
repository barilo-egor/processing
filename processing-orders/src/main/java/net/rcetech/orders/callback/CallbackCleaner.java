package net.rcetech.orders.callback;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import net.rcetech.domain.service.orders.ClientCallbackService;
import net.rcetech.domain.service.orders.MerchantCallbackService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CallbackCleaner {

    private static final Duration RETENTION = Duration.ofDays(14);

    private final ClientCallbackService clientCallbackService;

    private final MerchantCallbackService merchantCallbackService;

    public CallbackCleaner(ClientCallbackService clientCallbackService,
                           MerchantCallbackService merchantCallbackService) {
        this.clientCallbackService = clientCallbackService;
        this.merchantCallbackService = merchantCallbackService;
    }

    @Scheduled(fixedDelay = 12, timeUnit = TimeUnit.HOURS)
    @SchedulerLock(name = "CallbackCleaner.deleteOld", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void deleteOld() {
        Instant createdBefore = Instant.now().minus(RETENTION);
        int deleted = clientCallbackService.deleteFinishedBefore(createdBefore);
        if (deleted > 0) {
            log.info("Удалено {} завершённых КБ клиентам старше {} дней.", deleted, RETENTION.toDays());
        }
        int deletedMerchant = merchantCallbackService.deleteCreatedBefore(createdBefore);
        if (deletedMerchant > 0) {
            log.info("Удалено {} КБ мерчантов старше {} дней.", deletedMerchant, RETENTION.toDays());
        }
    }
}
