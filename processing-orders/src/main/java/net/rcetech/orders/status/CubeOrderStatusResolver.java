package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class CubeOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.CUBE, Merchant.CUBE_HIGH_CHECK, Merchant.CUBE_LOW_CHECK, Merchant.CUBE_SIM
        );
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "ACCEPTED" -> OrderStatus.NEW;
                    case "SUCCESS" -> OrderStatus.SUCCESS;
                    case "ERROR" -> OrderStatus.CANCELED;
                    case "APPEAL" -> OrderStatus.DISPUTE;
                    default -> null;
                }
        );
    }
}
