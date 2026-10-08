package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class LevelPayOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.MERIDIAN_PAY, Merchant.MERIDIAN_PAY_HIGH_CHECK, Merchant.MERIDIAN_PAY_LOW_CHECK,
                Merchant.MERIDIAN_PAY_NSPK, Merchant.MERIDIAN_PAY_SIM,
                Merchant.PLATA_18, Merchant.PLATA_PAYMENT
        );
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "PENDING" -> OrderStatus.NEW;
                    case "SUCCESS" -> OrderStatus.SUCCESS;
                    case "FAIL" -> OrderStatus.CANCELED;
                    default -> null;
                }
        );
    }
}
