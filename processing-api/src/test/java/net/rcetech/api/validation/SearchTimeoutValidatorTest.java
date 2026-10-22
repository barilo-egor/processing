package net.rcetech.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchTimeoutValidatorTest {

    private record Timeout(@SearchTimeout Integer seconds) {}

    private Validator validator(int minSeconds, int maxSeconds) {
        return Validation.byDefaultProvider().configure()
                .constraintValidatorFactory(new ConstraintValidatorFactory() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
                        return (T) new SearchTimeoutValidator(minSeconds, maxSeconds);
                    }

                    @Override
                    public void releaseInstance(ConstraintValidator<?, ?> instance) {
                    }
                })
                .buildValidatorFactory()
                .getValidator();
    }

    private List<String> violations(int minSeconds, int maxSeconds, Integer seconds) {
        return validator(minSeconds, maxSeconds).validate(new Timeout(seconds)).stream()
                .map(ConstraintViolation::getMessage)
                .toList();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "5,300,5",
            "5,300,300",
            "10,120,10",
            "10,120,120",
            "10,120,null"
    }, nullValues = "null")
    @DisplayName("Таймаут в границах из настроек или отсутствующий таймаут должен проходить проверку.")
    void isValid_shouldAcceptTimeoutWithinSettings(int minSeconds, int maxSeconds, Integer seconds) {
        assertTrue(violations(minSeconds, maxSeconds, seconds).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "5,300,4",
            "10,120,-5"
    })
    @DisplayName("Таймаут меньше минимума из настроек должен отклоняться с текстом о минимуме.")
    void isValid_shouldRejectTimeoutBelowMin(int minSeconds, int maxSeconds, int seconds) {
        assertEquals(List.of("Таймаут поиска реквизитов не может быть меньше " + minSeconds + " секунд."),
                violations(minSeconds, maxSeconds, seconds));
    }

    @ParameterizedTest
    @CsvSource({
            "5,300,301",
            "10,120,121"
    })
    @DisplayName("Таймаут больше максимума из настроек должен отклоняться с текстом о максимуме.")
    void isValid_shouldRejectTimeoutAboveMax(int minSeconds, int maxSeconds, int seconds) {
        assertEquals(List.of("Таймаут поиска реквизитов не может превышать " + maxSeconds + " секунд."),
                violations(minSeconds, maxSeconds, seconds));
    }
}
