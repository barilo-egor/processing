package net.rcetech.orders.callback;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import net.rcetech.domain.model.orders.ClientCallback;
import net.rcetech.domain.service.orders.ClientCallbackService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CallbackDispatcher {

    private static final int BATCH_SIZE = 100;

    private static final Duration SENDING_TIMEOUT = Duration.ofSeconds(30);

    private final ClientCallbackService clientCallbackService;

    private final CallbackHttpSender callbackHttpSender;

    private final Executor virtualTaskExecutor;

    private final long delayMillis;

    private final double multiplier;

    private final long maxDelayMillis;

    private final int maxRetries;

    public CallbackDispatcher(ClientCallbackService clientCallbackService, CallbackHttpSender callbackHttpSender,
                              @Qualifier("virtualTaskExecutor") Executor virtualTaskExecutor,
                              @Value("${app.retry.callback.delay:10000}") long delayMillis,
                              @Value("${app.retry.callback.multiplier:3.0}") double multiplier,
                              @Value("${app.retry.callback.maxDelay:120000}") long maxDelayMillis,
                              @Value("${app.retry.callback.maxRetries:3}") int maxRetries) {
        this.clientCallbackService = clientCallbackService;
        this.callbackHttpSender = callbackHttpSender;
        this.virtualTaskExecutor = virtualTaskExecutor;
        this.delayMillis = delayMillis;
        this.multiplier = multiplier;
        this.maxDelayMillis = maxDelayMillis;
        this.maxRetries = maxRetries;
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = "CallbackDispatcher.dispatch", lockAtMostFor = "PT2M")
    public void dispatch() {
        failStuckInSending();
        List<ClientCallback> callbacks = clientCallbackService.findReadyToSend(BATCH_SIZE);
        if (callbacks.isEmpty()) {
            return;
        }
        CompletableFuture.allOf(callbacks.stream()
                        .map(callback -> CompletableFuture.runAsync(() -> attempt(callback), virtualTaskExecutor))
                        .toArray(CompletableFuture[]::new))
                .join();
    }

    private void failStuckInSending() {
        for (ClientCallback callback : clientCallbackService.findStuckInSending(Instant.now().minus(SENDING_TIMEOUT))) {
            long timestamp = System.currentTimeMillis();
            log.error("{} Неизвестен результат отправки КБ {} по ордеру {}: КБ находится в отправке с {}.",
                    timestamp, callback.getId(), callback.getOrderId(), callback.getLastAttemptAt());
            try {
                clientCallbackService.markDeliveryUnknown(callback.getId(), timestamp);
            } catch (Exception e) {
                log.error("Не удалось пометить КБ {} по ордеру {} недоставленным и создать инцидент: {}",
                        callback.getId(), callback.getOrderId(), e.getMessage(), e);
            }
        }
    }

    private void attempt(ClientCallback callback) {
        try {
            ClientCallback sending = clientCallbackService.markSending(callback.getId());
            callbackHttpSender.send(sending);
            clientCallbackService.markSent(sending.getId());
        } catch (CallbackSendException e) {
            handleFailure(e.getCallback(), e);
        } catch (Exception e) {
            log.error("Ошибка обработки КБ {} по ордеру {}: {}", callback.getId(), callback.getOrderId(),
                    e.getMessage(), e);
        }
    }

    private void handleFailure(ClientCallback callback, Exception e) {
        int attempts = callback.getAttempts();
        if (attempts > maxRetries) {
            log.warn("КБ {} по ордеру {} не доставлен после {} попыток: {}", callback.getId(),
                    callback.getOrderId(), attempts, e.getMessage());
            clientCallbackService.markFailed(callback.getId());
            return;
        }
        long delay = Math.min((long) (delayMillis * Math.pow(multiplier, attempts - 1)), maxDelayMillis);
        log.warn("КБ {} по ордеру {} не доставлен (попытка {}), повтор через {} мс: {}", callback.getId(),
                callback.getOrderId(), attempts, delay, e.getMessage());
        clientCallbackService.scheduleRetry(callback.getId(), Instant.now().plus(Duration.ofMillis(delay)));
    }
}
