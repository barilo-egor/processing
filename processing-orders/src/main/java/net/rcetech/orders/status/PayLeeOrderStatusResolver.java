package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class PayLeeOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(Merchant.PAY_LEE, Merchant.PAY_LEE_QR);
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "PENDING" -> OrderStatus.NEW;
                    case "COMPLETED" -> OrderStatus.SUCCESS;
                    case "CANCELED" -> OrderStatus.CANCELED;
                    case "TIMEOUT" -> OrderStatus.TIMEOUT;
                    default -> null;
                }
        );
    }
}
