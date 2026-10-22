package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class TronExOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.TRON_EX_BT, Merchant.TRON_EX_PDF, Merchant.TRON_EX_QR, Merchant.TRON_EX_SIM
        );
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "CREATED", "IN_PROGRESS" -> OrderStatus.NEW;
                    case "COMPLETED" -> OrderStatus.SUCCESS;
                    case "CANCELLED" -> OrderStatus.CANCELED;
                    default -> null;
                }
        );
    }
}
