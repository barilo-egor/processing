package net.rcetech.domain.util;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Общие хелперы для формирования JPA-спецификаций.
 */
@UtilityClass
public class SpecificationsUtil {

    private static final char LIKE_ESCAPE = '!';

    public static <T> void addSimpleEqual(List<Predicate> predicates, CriteriaBuilder builder,
                                          Expression<T> expression, T value) {
        if (Objects.nonNull(value)) {
            predicates.add(builder.equal(expression, value));
        }
    }

    public static <T> void addIn(List<Predicate> predicates, Expression<T> expression, Collection<T> values) {
        if (Objects.nonNull(values) && !values.isEmpty()) {
            predicates.add(expression.in(values));
        }
    }

    public static void addStringEqual(List<Predicate> predicates, CriteriaBuilder builder,
                                      Expression<String> expression, String value) {
        if (Objects.nonNull(value) && !value.isBlank()) {
            predicates.add(builder.equal(expression, value));
        }
    }

    public static void addStringContains(List<Predicate> predicates, CriteriaBuilder builder,
                                         Expression<String> expression, String value) {
        if (Objects.nonNull(value) && !value.isBlank()) {
            predicates.add(contains(builder, expression, value));
        }
    }

    public static Predicate contains(CriteriaBuilder builder, Expression<String> expression, String value) {
        String escaped = value
                .replace(String.valueOf(LIKE_ESCAPE), String.valueOf(LIKE_ESCAPE) + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
        return builder.like(expression, "%" + escaped + "%", LIKE_ESCAPE);
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
