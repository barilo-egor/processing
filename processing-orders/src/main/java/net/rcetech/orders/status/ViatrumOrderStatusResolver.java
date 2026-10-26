package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class ViatrumOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(Merchant.VIATRUM);
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "CREATED", "PENDING", "PROCESSING", "INCORRECT_AMOUNT", "REFUNDED" -> OrderStatus.NEW;
                    case "COMPLETED" -> OrderStatus.SUCCESS;
                    case "CANCELLED", "ERROR" -> OrderStatus.CANCELED;
                    case "TIMEOUT" -> OrderStatus.TIMEOUT;
                    default -> null;
                }
        );
    }
}
