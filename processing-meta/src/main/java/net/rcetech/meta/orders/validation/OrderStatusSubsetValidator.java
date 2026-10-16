package net.rcetech.meta.orders.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.rcetech.meta.orders.OrderStatus;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class OrderStatusSubsetValidator implements ConstraintValidator<OrderStatusSubset, OrderStatus> {

    private Set<OrderStatus> allowedStatuses;

    @Override
    public void initialize(OrderStatusSubset constraintAnnotation) {
        allowedStatuses = EnumSet.noneOf(OrderStatus.class);
        allowedStatuses.addAll(List.of(constraintAnnotation.anyOf()));
    }

    @Override
    public boolean isValid(OrderStatus value, ConstraintValidatorContext context) {
        return value == null || allowedStatuses.contains(value);
    }
}
