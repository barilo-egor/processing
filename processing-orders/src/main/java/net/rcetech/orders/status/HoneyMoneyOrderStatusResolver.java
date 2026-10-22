package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class HoneyMoneyOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(Merchant.HONEY_MONEY);
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "PENDING" -> OrderStatus.NEW;
                    case "SUCCESSFUL" -> OrderStatus.SUCCESS;
                    case "DENIED" -> OrderStatus.CANCELED;
                    default -> null;
                }
        );
    }
}
