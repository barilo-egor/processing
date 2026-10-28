package net.rcetech.meta.orders.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CallbackUrlValidator implements ConstraintValidator<CallbackUrl, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || CallbackUrlPolicy.isAllowed(value);
    }
}
