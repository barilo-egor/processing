package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class CashOutOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.CASH_OUT
        );
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "PENDING", "PROCESSING" -> OrderStatus.NEW;
                    case "COMPLETED", "COMPLETED_AFTER_DISPUTE" -> OrderStatus.SUCCESS;
                    case "FAILED", "CANCELLED", "FAILED_AFTER_DISPUTE" -> OrderStatus.CANCELED;
                    case "EXPIRE" -> OrderStatus.TIMEOUT;
                    case "APPEAL" -> OrderStatus.DISPUTE;
                    default -> null;
                }
        );
    }
}
