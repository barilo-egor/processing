package net.rcetech.domain.repository.orders;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.clients.Client_;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.model.orders.Order_;
import net.rcetech.domain.repository.clients.ClientSpecifications;
import net.rcetech.meta.orders.dto.ClientOrderFilter;
import net.rcetech.meta.orders.dto.OrderFilter;
import org.springframework.data.jpa.domain.PredicateSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static net.rcetech.domain.util.SpecificationsUtil.*;

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
            addIn(predicates, from.get(Order_.status), filter.status());
            addIn(predicates, from.get(Order_.method), filter.method());
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
            addDateRange(predicates, builder, from.get(Order_.createdAt), filter.createdAtFrom(), filter.createdAtTo());

            if (Objects.nonNull(filter.client()) && !filter.client().isBlank()) {
                predicates.add(ClientSpecifications.idOrUsername(filter.client(), from.join(Order_.client), builder));
            }
            return builder.and(predicates);
        };
    }
}
