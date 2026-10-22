package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class PayscrowOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.PAYSCROW, Merchant.PAYSCROW_LOW, Merchant.PAYSCROW_HIGH_CHECK,
                Merchant.PAYSCROW_SIM, Merchant.PAYSCROW_TRANSGRAN, Merchant.PAYSCROW_WHITE_TRIANGLE
        );
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "UNPAID" -> OrderStatus.NEW;
                    case "COMPLETED" -> OrderStatus.SUCCESS;
                    case "CANCELED_BY_SERVICE" -> OrderStatus.CANCELED;
                    case "CANCELED_BY_TIMEOUT" -> OrderStatus.TIMEOUT;
                    default -> null;
                }
        );
    }
}
