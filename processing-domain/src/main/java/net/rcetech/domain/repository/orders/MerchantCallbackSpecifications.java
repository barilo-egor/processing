package net.rcetech.domain.repository.orders;

import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;
import net.rcetech.domain.model.orders.MerchantCallback;
import net.rcetech.domain.model.orders.MerchantCallback_;
import net.rcetech.domain.model.orders.Order_;
import net.rcetech.meta.billing.dto.MerchantCallbackFilter;
import org.springframework.data.jpa.domain.PredicateSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static net.rcetech.domain.util.SpecificationsUtil.*;

@UtilityClass
public class MerchantCallbackSpecifications {

    public static PredicateSpecification<MerchantCallback> matches(MerchantCallbackFilter filter) {
        return ((from, builder) -> {
            if (Objects.isNull(filter)) {
                return builder.conjunction();
            }
            List<Predicate> predicates = new ArrayList<>();
            if (Objects.nonNull(filter.orderId())) {
                predicates.add(builder.equal(from.join(MerchantCallback_.order).get(Order_.id), filter.orderId()));
            }
            addSimpleEqual(predicates, builder, from.get(MerchantCallback_.merchant), filter.merchant());
            addStringEqual(predicates, builder, from.get(MerchantCallback_.merchantOrderId), filter.merchantOrderId());
            addDateRange(predicates, builder, from.get(MerchantCallback_.createdAt), filter.createdAtFrom(), filter.createdAtTo());
            return builder.and(predicates);
        });
    }
}
