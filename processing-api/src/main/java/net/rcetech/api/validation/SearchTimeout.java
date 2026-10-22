package net.rcetech.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SearchTimeoutValidator.class)
public @interface SearchTimeout {

    String message() default "Недопустимый таймаут поиска реквизитов.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
