package net.rcetech.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

public class SearchTimeoutValidator implements ConstraintValidator<SearchTimeout, Integer> {

    private final int minSeconds;

    private final int maxSeconds;

    public SearchTimeoutValidator(@Value("${details-search.timeout.min-seconds:5}") int minSeconds,
                                  @Value("${details-search.timeout.max-seconds:60}") int maxSeconds) {
        this.minSeconds = minSeconds;
        this.maxSeconds = maxSeconds;
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (value < minSeconds) {
            return violation(context, "Таймаут поиска реквизитов не может быть меньше " + minSeconds + " секунд.");
        }
        if (value > maxSeconds) {
            return violation(context, "Таймаут поиска реквизитов не может превышать " + maxSeconds + " секунд.");
        }
        return true;
    }

    private boolean violation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
