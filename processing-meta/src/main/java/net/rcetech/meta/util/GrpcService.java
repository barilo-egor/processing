package net.rcetech.meta.util;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Базовый класс для gRPC-клиентов с утилитами работы с асинхронными вызовами.
 */
public abstract class GrpcService {

    /**
     * Переводит Guava {@link ListenableFuture} в {@link CompletableFuture}.
     *
     * @param listenableFuture исходный future gRPC-вызова
     * @param <T>              тип результата
     * @return completable future с тем же результатом или исключением
     */
    protected <T> CompletableFuture<T> toCompletableFuture(ListenableFuture<T> listenableFuture) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();

        listenableFuture.addListener(() -> {
            try {
                completableFuture.complete(listenableFuture.get());
            } catch (ExecutionException e) {
                completableFuture.completeExceptionally(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                completableFuture.completeExceptionally(e);
            } catch (Exception e) {
                completableFuture.completeExceptionally(e);
            }
        }, Runnable::run);

        return completableFuture;
    }

}
