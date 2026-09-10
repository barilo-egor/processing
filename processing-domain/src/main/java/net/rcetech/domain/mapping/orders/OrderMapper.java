package net.rcetech.domain.mapping.orders;

import net.rcetech.domain.model.orders.Order;
import net.rcetech.meta.orders.dto.ClientOrderSummary;
import org.mapstruct.BeanMapping;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface OrderMapper {

    @BeanMapping(unmappedSourcePolicy = ReportingPolicy.IGNORE)
    ClientOrderSummary toOrderSummary(Order order);


}
