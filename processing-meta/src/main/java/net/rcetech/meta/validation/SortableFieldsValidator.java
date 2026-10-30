package net.rcetech.meta.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public class SortableFieldsValidator implements ConstraintValidator<SortableFields, Pageable> {

    private Set<String> sortableFields;

    @Override
    public void initialize(SortableFields constraintAnnotation) {
        sortableFields = Set.of(constraintAnnotation.value());
    }

    @Override
    public boolean isValid(Pageable value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        for (Sort.Order order : value.getSort()) {
            if (!sortableFields.contains(order.getProperty())) {
                context.unwrap(HibernateConstraintValidatorContext.class)
                        .addMessageParameter("property", escape(order.getProperty()));
                return false;
            }
        }
        return true;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("$", "\\$");
    }
}
