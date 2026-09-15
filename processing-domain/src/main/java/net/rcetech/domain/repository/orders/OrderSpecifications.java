package net.rcetech.domain.repository.orders;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.clients.Client_;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.model.orders.Order_;
import net.rcetech.meta.orders.dto.ClientOrderFilter;
import net.rcetech.meta.orders.dto.OrderFilter;
import org.springframework.data.jpa.domain.PredicateSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@UtilityClass
public class OrderSpecifications {

    public static PredicateSpecification<Order> matches(UUID clientID, ClientOrderFilter filter) {
        return (from, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Order, Client> join = from.join(Order_.client);
            predicates.add(builder.equal(join.get(Client_.id), clientID));
            if (Objects.isNull(filter)) {
                return builder.and(predicates.toArray(new Predicate[0]));
            }
            if (Objects.nonNull(filter.status())) {
                predicates.add(builder.equal(from.get(Order_.status), filter.status()));
            }
            if (Objects.nonNull(filter.method())) {
                predicates.add(builder.equal(from.get(Order_.method), filter.method()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static PredicateSpecification<Order> matches(OrderFilter filter) {
        return (from, builder) -> {
            if (Objects.isNull(filter)) {
                return builder.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();

            addSimpleEqual(predicates, builder, from.get(Order_.id), filter.id());
            addStringEqual(predicates, builder, from.get(Order_.internalId), filter.internalId());
            addSimpleEqual(predicates, builder, from.get(Order_.status), filter.status());
            addSimpleEqual(predicates, builder, from.get(Order_.merchant), filter.merchant());
            addStringEqual(predicates, builder, from.get(Order_.merchantOrderId), filter.merchantOrderId());

            if (Objects.nonNull(filter.createdAtFrom())) {
                predicates.add(builder.greaterThan(from.get(Order_.createdAt), filter.createdAtFrom()));
            }
            if (Objects.nonNull(filter.createdAtTo())) {
                predicates.add(builder.lessThanOrEqualTo(from.get(Order_.createdAt), filter.createdAtTo()));
            }

            if (Objects.nonNull(filter.client()) && !filter.client().isBlank()) {
                Join<Order, Client> join = from.join(Order_.client);
                UUID clientId;
                try {
                    clientId =  UUID.fromString(filter.client());
                } catch (IllegalArgumentException e) {
                    clientId = null;
                }
                if (Objects.nonNull(clientId)) {
                    predicates.add(builder.or(
                            builder.equal(join.get(Client_.id), UUID.fromString(filter.client())),
                            builder.equal(join.get(Client_.username), filter.client())
                    ));
                } else {
                    predicates.add(builder.equal(join.get(Client_.username), filter.client()));
                }
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static <T> void addSimpleEqual(List<Predicate> predicates, CriteriaBuilder builder,
                                           Expression<T> expression, T value) {
        if (Objects.nonNull(value)) {
            predicates.add(builder.equal(expression, value));
        }
    }

    private static <T> void addStringEqual(List<Predicate> predicates, CriteriaBuilder builder,
                                           Expression<T> expression, String value) {
        if (Objects.nonNull(value) && !value.isBlank()) {
            predicates.add(builder.equal(expression, value));
        }
    }

}
