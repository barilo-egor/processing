package net.rcetech.domain.repository.billing;

import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;
import net.rcetech.domain.model.billing.WithdrawalRequest;
import net.rcetech.domain.model.billing.WithdrawalRequest_;
import net.rcetech.domain.model.clients.Client_;
import net.rcetech.domain.repository.clients.ClientSpecifications;
import net.rcetech.meta.billing.dto.WithdrawalRequestFilter;
import org.springframework.data.jpa.domain.PredicateSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static net.rcetech.domain.util.SpecificationsUtil.*;

@UtilityClass
public class WithdrawalRequestSpecifications {

    public static PredicateSpecification<WithdrawalRequest> matches(@Nullable UUID clientId,
                                                                    WithdrawalRequestFilter filter) {
        return (from, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (Objects.nonNull(clientId)) {
                predicates.add(builder.equal(from.join(WithdrawalRequest_.client).get(Client_.id), clientId));
                if (Objects.isNull(filter)) {
                    return builder.and(predicates);
                }
            } else if (Objects.nonNull(filter.client()) && !filter.client().isBlank()) {
                predicates.add(ClientSpecifications.idOrUsername(filter.client(),
                        from.join(WithdrawalRequest_.client), builder));
            }
            addSimpleEqual(predicates, builder, from.get(WithdrawalRequest_.id), filter.id());
            addSimpleEqual(predicates, builder, from.get(WithdrawalRequest_.status), filter.status());
            addDateRange(predicates, builder, from.get(WithdrawalRequest_.createdAt), filter.createdAtFrom(), filter.createdAtTo());
            addStringEqual(predicates, builder, from.get(WithdrawalRequest_.address), filter.address());
            return builder.and(predicates);
        };
    }
}
