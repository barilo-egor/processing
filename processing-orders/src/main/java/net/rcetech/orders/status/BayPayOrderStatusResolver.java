package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class BayPayOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.BAY_PAY
        );
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "ACTIVE" -> OrderStatus.NEW;
                    case "COMPLETED" -> OrderStatus.SUCCESS;
                    case "CANCELLED" -> OrderStatus.CANCELED;
                    default -> null;
                }
        );
    }
}
