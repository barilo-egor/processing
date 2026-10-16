package net.rcetech.meta.orders.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.rcetech.meta.orders.OrderStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStatusSubsetValidatorTest {

    record Target(@OrderStatusSubset(anyOf = {OrderStatus.SUCCESS, OrderStatus.CANCELED}) OrderStatus status) { }

    private static ValidatorFactory validatorFactory;

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"SUCCESS", "CANCELED"})
    @NullSource
    @DisplayName("Валидатор должен пропустить статус из списка допустимых или null.")
    void isValid_shouldPassAllowedStatusOrNull(OrderStatus status) {
        assertTrue(validator.validate(new Target(status)).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"NEW", "TIMEOUT", "DISPUTE"})
    @DisplayName("Валидатор должен вернуть нарушение по полю status, если статус не входит в список допустимых.")
    void isValid_shouldRejectNotAllowedStatus(OrderStatus status) {
        Set<ConstraintViolation<Target>> violations = validator.validate(new Target(status));

        assertEquals(1, violations.size());
        ConstraintViolation<Target> violation = violations.iterator().next();
        assertEquals("status", violation.getPropertyPath().toString());
        assertEquals("Недопустимый статус ордера.", violation.getMessage());
    }
}
