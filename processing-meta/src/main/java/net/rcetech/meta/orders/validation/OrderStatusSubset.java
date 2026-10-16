package net.rcetech.meta.orders.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import net.rcetech.meta.orders.OrderStatus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OrderStatusSubsetValidator.class)
public @interface OrderStatusSubset {

    OrderStatus[] anyOf();

    String message() default "Недопустимый статус ордера.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
