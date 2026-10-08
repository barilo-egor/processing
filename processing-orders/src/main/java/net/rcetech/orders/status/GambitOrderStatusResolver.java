package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class GambitOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.GAMBIT, Merchant.GAMBIT_SIM,
                Merchant.HESOYAM, Merchant.HESOYAM_SIM
        );
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "PENDING", "PROCESSING", "AWAITING_FUNDS" -> OrderStatus.NEW;
                    case "SUCCEEDED" -> OrderStatus.SUCCESS;
                    case "FAILED", "CANCELLED", "REFUNDED" -> OrderStatus.CANCELED;
                    default -> null;
                }
        );
    }
}
