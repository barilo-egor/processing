package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class BitZoneOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(Merchant.BIT_ZONE);
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "PENDING", "ACTIVE", "CHECKING", "RE_CALCULATION" -> OrderStatus.NEW;
                    case "CLOSED" -> OrderStatus.SUCCESS;
                    case "CANCELED" -> OrderStatus.CANCELED;
                    case "DISPUTE" -> OrderStatus.DISPUTE;
                    default -> null;
                }
        );
    }
}
