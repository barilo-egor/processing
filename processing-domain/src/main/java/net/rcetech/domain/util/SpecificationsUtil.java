package net.rcetech.domain.util;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Общие хелперы для формирования JPA-спецификаций.
 */
@UtilityClass
public class SpecificationsUtil {

    public static <T> void addSimpleEqual(List<Predicate> predicates, CriteriaBuilder builder,
                                          Expression<T> expression, T value) {
        if (Objects.nonNull(value)) {
            predicates.add(builder.equal(expression, value));
        }
    }

    public static void addStringEqual(List<Predicate> predicates, CriteriaBuilder builder,
                                      Expression<String> expression, String value) {
        if (Objects.nonNull(value) && !value.isBlank()) {
            predicates.add(builder.equal(expression, value));
        }
    }

    public static void addDateRange(List<Predicate> predicates, CriteriaBuilder builder,
                                    Expression<Instant> expression, Instant from, Instant to) {
        if (Objects.nonNull(from)) {
            predicates.add(builder.greaterThanOrEqualTo(expression, from));
        }
        if (Objects.nonNull(to)) {
            predicates.add(builder.lessThan(expression, to));
        }
    }
}
