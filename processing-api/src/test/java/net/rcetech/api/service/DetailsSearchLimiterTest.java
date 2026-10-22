package net.rcetech.api.service;

import net.rcetech.meta.exception.ServiceUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DetailsSearchLimiterTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    @DisplayName("Метод должен бросить ServiceUnavailableException, если все разрешения заняты.")
    void acquire_shouldThrowIfLimitReached(int concurrencyLimit) {
        DetailsSearchLimiter limiter = new DetailsSearchLimiter(concurrencyLimit);
        for (int i = 0; i < concurrencyLimit; i++) {
            assertDoesNotThrow(limiter::acquire);
        }

        assertThrows(ServiceUnavailableException.class, limiter::acquire);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    @DisplayName("После освобождения разрешение должно снова выдаваться.")
    void release_shouldReturnPermit(int concurrencyLimit) {
        DetailsSearchLimiter limiter = new DetailsSearchLimiter(concurrencyLimit);
        for (int i = 0; i < concurrencyLimit; i++) {
            limiter.acquire();
        }

        limiter.release();

        assertDoesNotThrow(limiter::acquire);
        assertThrows(ServiceUnavailableException.class, limiter::acquire);
    }
}
