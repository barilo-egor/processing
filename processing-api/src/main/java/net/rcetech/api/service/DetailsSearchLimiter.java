package net.rcetech.api.service;

import net.rcetech.meta.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

@Component
public class DetailsSearchLimiter {

    private final Semaphore permits;

    public DetailsSearchLimiter(@Value("${details-search.concurrency-limit:200}") int concurrencyLimit) {
        this.permits = new Semaphore(concurrencyLimit);
    }

    public void acquire() {
        if (!permits.tryAcquire()) {
            throw new ServiceUnavailableException("Достигнут лимит одновременных поисков реквизитов.");
        }
    }

    public void release() {
        permits.release();
    }
}
