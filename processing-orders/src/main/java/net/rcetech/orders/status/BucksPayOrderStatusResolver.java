package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class BucksPayOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.BUCKS_PAY, Merchant.BUCKS_PAY_SIM
        );
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "CREATED", "WAITING_FOR_PAYMENT", "WAITING_FOR_TRADER", "INCORRECT_AMOUNT", "RESTORED",
                            "REVERTED" -> OrderStatus.NEW;
                    case "PAID" -> OrderStatus.SUCCESS;
                    case "CANCELLED" -> OrderStatus.CANCELED;
                    case "TIMEOUT" -> OrderStatus.TIMEOUT;
                    default -> null;
                }
        );
    }
}
