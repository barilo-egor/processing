package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class OnlyPaysOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(Merchant.ONLY_PAYS);
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "WAITING" -> OrderStatus.NEW;
                    case "CANCELED" -> OrderStatus.CANCELED;
                    case "FINISHED" -> OrderStatus.SUCCESS;
                    default -> null;
                }
        );
    }
}
