package net.rcetech.domain.repository.clients;

import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.clients.Client_;
import net.rcetech.meta.clients.dto.ClientFilter;
import org.springframework.data.jpa.domain.PredicateSpecification;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class ClientSpecifications {

    public static PredicateSpecification<Client> matches(ClientFilter clientFilter) {
        return (from, builder) -> {
            if (clientFilter == null) {
                return builder.conjunction();
            }
            List<Predicate> predicates = new ArrayList<>();
            if (clientFilter.id() != null) {
                predicates.add(builder.equal(from.get(Client_.id), clientFilter.id()));
            }
            if (clientFilter.username() != null && !clientFilter.username().isBlank()) {
                predicates.add(builder.equal(from.get(Client_.username), clientFilter.username()));
            }
            if (clientFilter.status() != null) {
                predicates.add(builder.equal(from.get(Client_.status), clientFilter.status()));
            }
            if (clientFilter.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(from.get(Client_.registeredAt), clientFilter.from()));
            }
            if (clientFilter.to() != null) {
                predicates.add(builder.lessThan(from.get(Client_.registeredAt), clientFilter.to()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
