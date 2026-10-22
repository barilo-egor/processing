package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class PaySyncOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.PAYSYNC
        );
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "WAIT", "PENDING" -> OrderStatus.NEW;
                    case "PAID" -> OrderStatus.SUCCESS;
                    case "FAILED" -> OrderStatus.CANCELED;
                    default -> null;
                }
        );
    }
}
