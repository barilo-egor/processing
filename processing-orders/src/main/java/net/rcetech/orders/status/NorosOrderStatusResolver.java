package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class NorosOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.NOROS, Merchant.NOROS_HIGH_CHECK
        );
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "CREATED", "OFFER", "ACCEPTED", "PROOF" -> OrderStatus.NEW;
                    case "COMPLETED" -> OrderStatus.SUCCESS;
                    case "CANCELED" -> OrderStatus.CANCELED;
                    default -> null;
                }
        );
    }
}
