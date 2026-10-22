package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class ManyPayOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.MANY_PAY, Merchant.MANY_PAY_HIGH_CHECK, Merchant.MANY_PAY_LOW_CHECK
        );
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "PENDING", "WAITING_PAYMENT" -> OrderStatus.NEW;
                    case "PAID" -> OrderStatus.SUCCESS;
                    case "CANCELLED" -> OrderStatus.CANCELED;
                    case "EXPIRED" -> OrderStatus.TIMEOUT;
                    default -> null;
                }
        );
    }
}
