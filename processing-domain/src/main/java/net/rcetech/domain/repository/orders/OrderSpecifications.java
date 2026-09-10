package net.rcetech.domain.repository.orders;

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
            if (Objects.nonNull(filter.id())) {
                predicates.add(builder.equal(from.get(Order_.id), filter.id()));
            }
            if (Objects.nonNull(filter.internalId())) {
                predicates.add(builder.equal(from.get(Order_.internalId), filter.internalId()));
            }
            if (Objects.nonNull(filter.status())) {
                predicates.add(builder.equal(from.get(Order_.status), filter.status()));
            }
            if (Objects.nonNull(filter.clientId())) {
                Join<Order, Client> join = from.join(Order_.client);
                predicates.add(builder.equal(join.get(Client_.id), filter.clientId()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
