package net.rcetech.domain.repository.billing;

import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;
import net.rcetech.domain.model.billing.Transaction;
import net.rcetech.domain.model.billing.Transaction_;
import net.rcetech.domain.model.clients.Client_;
import net.rcetech.domain.repository.clients.ClientSpecifications;
import net.rcetech.meta.billing.dto.ClientTransactionFilter;
import net.rcetech.meta.billing.dto.TransactionFilter;
import org.springframework.data.jpa.domain.PredicateSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
            return builder.and(predicates);
        });
    }

    public static PredicateSpecification<Transaction> matches(UUID clientId, ClientTransactionFilter filter) {
        return (from, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(from.join(Transaction_.client).get(Client_.id), clientId));
            if (Objects.isNull(filter)) {
                return builder.and(predicates.toArray(new Predicate[0]));
            }
            if (Objects.nonNull(filter.createdAtFrom())) {
                predicates.add(builder.greaterThan(from.get(Transaction_.createdAt), filter.createdAtFrom()));
            }
            if (Objects.nonNull(filter.createdAtTo())) {
                predicates.add(builder.lessThanOrEqualTo(from.get(Transaction_.createdAt), filter.createdAtTo()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

}
