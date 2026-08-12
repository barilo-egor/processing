package net.rcetech.billing.utils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.experimental.UtilityClass;
import net.rcetech.meta.billing.dto.GetTransactionsRequest;
import net.rcetech.domain.model.billing.Transaction;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Утилита для формирования JPA-спецификаций сущности {@link Transaction}.
 */
@UtilityClass
public class TransactionSpecification {

    public static Specification<Transaction> buildSpecification(GetTransactionsRequest request) {
        return (root, query, cb) -> cb.and(toPredicates(root, cb, request));
    }

    private static Predicate[] toPredicates(Root<Transaction> root, CriteriaBuilder cb, GetTransactionsRequest request) {
        List<Predicate> predicates = new ArrayList<>();

        if (request.id() != null) {
            predicates.add(cb.equal(root.get("id"), request.id()));
        }

        if (request.clientIds() != null && !request.clientIds().isEmpty()) {
            predicates.add(root.get("clientId").in(request.clientIds()));
        }

        if (request.minAmount() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), request.minAmount()));
        }

        if (request.maxAmount() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("amount"), request.maxAmount()));
        }

        if (request.operations() != null && !request.operations().isEmpty()) {
            predicates.add(root.get("operation").in(request.operations()));
        }

        if (request.createdAtFrom() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), request.createdAtFrom()));
        }

        if (request.createdAtTo() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), request.createdAtTo()));
        }

        return predicates.toArray(new Predicate[0]);
    }

}
