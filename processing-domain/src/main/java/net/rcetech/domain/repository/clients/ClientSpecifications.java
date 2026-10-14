package net.rcetech.domain.repository.clients;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.clients.Client_;
import net.rcetech.meta.clients.dto.ClientFilter;
import org.springframework.data.jpa.domain.PredicateSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static net.rcetech.domain.util.SpecificationsUtil.*;

@UtilityClass
public class ClientSpecifications {

    public static PredicateSpecification<Client> matches(ClientFilter clientFilter) {
        return (from, builder) -> {
            if (clientFilter == null) {
                return builder.conjunction();
            }
            List<Predicate> predicates = new ArrayList<>();
            addSimpleEqual(predicates, builder, from.get(Client_.id), clientFilter.id());
            addStringEqual(predicates, builder, from.get(Client_.username), clientFilter.username());
            addDateRange(predicates, builder, from.get(Client_.registeredAt), clientFilter.from(), clientFilter.to());
            return builder.and(predicates);
        };
    }

    public static <T> Predicate idOrUsername(String query, Join<T, Client> join, CriteriaBuilder builder) {
        UUID clientId;
        try {
            clientId =  UUID.fromString(query);
        } catch (IllegalArgumentException e) {
            clientId = null;
        }
        if (Objects.nonNull(clientId)) {
            return builder.or(
                    builder.equal(join.get(Client_.id), UUID.fromString(query)),
                    builder.equal(join.get(Client_.username), query)
            );
        } else {
            return builder.equal(join.get(Client_.username), query);
        }
    }
}
