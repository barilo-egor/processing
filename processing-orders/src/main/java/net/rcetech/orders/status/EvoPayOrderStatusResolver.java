package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class EvoPayOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.EVO_PAY
        );
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "CREATED", "IN_PROCESS" -> OrderStatus.NEW;
                    case "SUCCESS" -> OrderStatus.SUCCESS;
                    case "CANCEL" -> OrderStatus.CANCELED;
                    case "EXPIRE" -> OrderStatus.TIMEOUT;
                    case "APPEAL" -> OrderStatus.DISPUTE;
                    default -> null;
                }
        );
    }
}
