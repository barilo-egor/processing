package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class RsPayOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.RS_PAY, Merchant.RS_PAY_BT
        );
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "AVAILABLE", "PENDING", "PROCESSING", "NO_REQUISITES", "REFUNDED",
                         "PARTIAL_REFUNDED" -> OrderStatus.NEW;
                    case "SUCCESS" -> OrderStatus.SUCCESS;
                    case "FAILED", "CANCELLED" -> OrderStatus.CANCELED;
                    default -> null;
                }
        );
    }
}
