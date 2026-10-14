package net.rcetech.domain.repository.incidents;

import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;
import net.rcetech.domain.model.incidents.Incident;
import net.rcetech.domain.model.incidents.Incident_;
import net.rcetech.meta.incidents.dto.IncidentFilter;
import org.springframework.data.jpa.domain.PredicateSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static net.rcetech.domain.util.SpecificationsUtil.addDateRange;
import static net.rcetech.domain.util.SpecificationsUtil.addSimpleEqual;

@UtilityClass
public class IncidentSpecifications {

    public static PredicateSpecification<Incident> matches(IncidentFilter filter) {
        return ((from, builder) -> {
            if (Objects.isNull(filter)) {
                return builder.conjunction();
            }
            List<Predicate> predicates = new ArrayList<>();
            addSimpleEqual(predicates, builder, from.get(Incident_.id), filter.id());
            addDateRange(predicates, builder, from.get(Incident_.createdAt), filter.createdAtFrom(), filter.createdAtTo());
            addSimpleEqual(predicates, builder, from.get(Incident_.status), filter.status());
            addSimpleEqual(predicates, builder, from.get(Incident_.type), filter.type());
            addDateRange(predicates, builder, from.get(Incident_.resolvedAt), filter.resolvedAtFrom(), filter.resolvedAtTo());
            return builder.and(predicates);
        });
    }
}
