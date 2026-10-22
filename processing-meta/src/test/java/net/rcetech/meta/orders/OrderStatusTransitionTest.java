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
            "DISPUTE,DISPUTE,SKIPPED",
            "SEARCHING,SEARCHING,SKIPPED",
            "SEARCHING,DETAILS_NOT_FOUND,ALLOWED",
            "SEARCHING,NEW,ALLOWED",
            "SEARCHING,SUCCESS,REJECTED",
            "SEARCHING,CANCELED,ALLOWED",
            "SEARCHING,TIMEOUT,REJECTED",
            "SEARCHING,DISPUTE,REJECTED",
            "DETAILS_NOT_FOUND,SEARCHING,REJECTED",
            "DETAILS_NOT_FOUND,DETAILS_NOT_FOUND,SKIPPED",
            "DETAILS_NOT_FOUND,NEW,SKIPPED",
            "DETAILS_NOT_FOUND,SUCCESS,SKIPPED",
            "DETAILS_NOT_FOUND,CANCELED,SKIPPED",
            "DETAILS_NOT_FOUND,TIMEOUT,SKIPPED",
            "DETAILS_NOT_FOUND,DISPUTE,SKIPPED",
            "NEW,SEARCHING,REJECTED",
            "NEW,DETAILS_NOT_FOUND,SKIPPED",
            "SUCCESS,SEARCHING,REJECTED",
            "SUCCESS,DETAILS_NOT_FOUND,SKIPPED",
            "CANCELED,SEARCHING,REJECTED",
            "CANCELED,DETAILS_NOT_FOUND,SKIPPED",
            "TIMEOUT,SEARCHING,REJECTED",
            "TIMEOUT,DETAILS_NOT_FOUND,SKIPPED",
            "DISPUTE,SEARCHING,REJECTED",
            "DISPUTE,DETAILS_NOT_FOUND,SKIPPED"
    })
    @DisplayName("Метод должен вернуть результат перехода согласно таблице переходов статусов ордера.")
    void of_shouldReturnTransitionByTable(OrderStatus from, OrderStatus to, OrderStatusTransition expected) {
        assertEquals(expected, OrderStatusTransition.of(from, to));
    }
}
