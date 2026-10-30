package net.rcetech.meta.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SortableFieldsValidator.class)
public @interface SortableFields {

    String[] value();

    String message() default "Сортировка по полю «{property}» не поддерживается.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
