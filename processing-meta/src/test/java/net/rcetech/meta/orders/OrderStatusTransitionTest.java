package net.rcetech.meta.orders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderStatusTransitionTest {

    @ParameterizedTest
    @CsvSource({
            "NEW,NEW,SKIPPED",
            "NEW,SUCCESS,ALLOWED",
            "NEW,CANCELED,ALLOWED",
            "NEW,TIMEOUT,ALLOWED",
            "NEW,DISPUTE,ALLOWED",
            "SUCCESS,NEW,SKIPPED",
            "SUCCESS,SUCCESS,SKIPPED",
            "SUCCESS,CANCELED,REJECTED",
            "SUCCESS,TIMEOUT,SKIPPED",
            "SUCCESS,DISPUTE,REJECTED",
            "CANCELED,NEW,SKIPPED",
            "CANCELED,SUCCESS,ALLOWED",
            "CANCELED,CANCELED,SKIPPED",
            "CANCELED,TIMEOUT,SKIPPED",
            "CANCELED,DISPUTE,ALLOWED",
            "TIMEOUT,NEW,SKIPPED",
            "TIMEOUT,SUCCESS,ALLOWED",
            "TIMEOUT,CANCELED,SKIPPED",
            "TIMEOUT,TIMEOUT,SKIPPED",
            "TIMEOUT,DISPUTE,ALLOWED",
            "DISPUTE,NEW,SKIPPED",
            "DISPUTE,SUCCESS,ALLOWED",
            "DISPUTE,CANCELED,ALLOWED",
            "DISPUTE,TIMEOUT,SKIPPED",
            "DISPUTE,DISPUTE,SKIPPED"
    })
    @DisplayName("Метод должен вернуть результат перехода согласно таблице переходов статусов ордера.")
    void of_shouldReturnTransitionByTable(OrderStatus from, OrderStatus to, OrderStatusTransition expected) {
        assertEquals(expected, OrderStatusTransition.of(from, to));
    }
}
