package net.rcetech.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DetailsSearchExecutorConfigTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    @DisplayName("Пул поиска должен выполнять задачи в виртуальных потоках и отклонять задачи сверх лимита.")
    void detailsSearchExecutor_shouldRejectTasksOverConcurrencyLimit(int concurrencyLimit) throws Exception {
        SimpleAsyncTaskExecutor executor = new DetailsSearchExecutorConfig().detailsSearchExecutor(concurrencyLimit);
        CountDownLatch started = new CountDownLatch(concurrencyLimit);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean virtual = new AtomicBoolean(true);
        try {
            for (int i = 0; i < concurrencyLimit; i++) {
                executor.execute(() -> {
                    virtual.compareAndSet(true, Thread.currentThread().isVirtual());
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertTrue(started.await(5, TimeUnit.SECONDS));

            assertThrows(TaskRejectedException.class, () -> executor.execute(() -> { }));
            assertTrue(virtual.get());
        } finally {
            release.countDown();
            executor.close();
        }
    }
}
