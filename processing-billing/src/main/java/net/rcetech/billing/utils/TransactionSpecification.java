package net.rcetech.billing.utils;

import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;
import net.rcetech.domain.model.billing.Transaction;
import net.rcetech.domain.model.billing.Transaction_;
import net.rcetech.domain.repository.clients.ClientSpecifications;
import net.rcetech.meta.billing.dto.TransactionFilter;
import org.springframework.data.jpa.domain.PredicateSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Утилита для формирования JPA-спецификаций сущности {@link Transaction}.
 */
@UtilityClass
public class TransactionSpecification {

    public static PredicateSpecification<Transaction> matches(TransactionFilter filter) {
        return ((from, builder) -> {
            if (Objects.isNull(filter)) {
                return builder.conjunction();
            }
            List<Predicate> predicates = new ArrayList<>();
            if (Objects.nonNull(filter.client()) && !filter.client().isBlank()) {
                predicates.add(ClientSpecifications.idOrUsername(filter.client(), from.join(Transaction_.client), builder));
            }
            if (Objects.nonNull(filter.createdAtFrom())) {
                predicates.add(builder.greaterThan(from.get(Transaction_.createdAt), filter.createdAtFrom()));
            }
            if (Objects.nonNull(filter.createdAtTo())) {
                predicates.add(builder.lessThanOrEqualTo(from.get(Transaction_.createdAt), filter.createdAtTo()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        });
    }

}
