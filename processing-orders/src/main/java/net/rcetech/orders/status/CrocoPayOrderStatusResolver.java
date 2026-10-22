package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class CrocoPayOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.CROCO_PAY,
                Merchant.BASE_51, Merchant.BASE_51_HIGH_CHECK, Merchant.BASE_51_LOW_CHECK, Merchant.BASE_51_SIM
        );
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "PENDING" -> OrderStatus.NEW;
                    case "SUCCESS" -> OrderStatus.SUCCESS;
                    case "CANCELLED" -> OrderStatus.CANCELED;
                    case "EXPIRED" -> OrderStatus.TIMEOUT;
                    case "DISPUTE" -> OrderStatus.DISPUTE;
                    default -> null;
                }
        );
    }
}
