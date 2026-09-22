package net.rcetech.domain.repository.billing;

import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;
import net.rcetech.domain.model.billing.WithdrawalRequest;
import net.rcetech.domain.model.billing.WithdrawalRequest_;
import net.rcetech.domain.model.clients.Client_;
import net.rcetech.meta.billing.dto.ClientWithdrawalRequestFilter;
import org.springframework.data.jpa.domain.PredicateSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@UtilityClass
public class WithdrawalRequestSpecifications {

    public static PredicateSpecification<WithdrawalRequest> matches(UUID clientId, ClientWithdrawalRequestFilter filter) {
        return (from, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(from.join(WithdrawalRequest_.client).get(Client_.id), clientId));
            if (Objects.isNull(filter)) {
                return builder.and(predicates);
            }
            if (Objects.nonNull(filter.id())) {
                predicates.add(builder.equal(from.get(WithdrawalRequest_.id), filter.id()));
            }
            if (Objects.nonNull(filter.status())) {
                predicates.add(builder.equal(from.get(WithdrawalRequest_.status), filter.status()));
            }
            if (Objects.nonNull(filter.createdAtFrom())) {
                predicates.add(builder.greaterThan(from.get(WithdrawalRequest_.createdAt), filter.createdAtFrom()));
            }
            if (Objects.nonNull(filter.createdAtTo())) {
                predicates.add(builder.lessThanOrEqualTo(from.get(WithdrawalRequest_.createdAt), filter.createdAtTo()));
            }
            if (Objects.nonNull(filter.address()) && !filter.address().isBlank()) {
                predicates.add(builder.equal(from.get(WithdrawalRequest_.address), filter.address()));
            }
            return builder.and(predicates);
        };
    }
}
