package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class FiatCutOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.FIAT_CUT
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
