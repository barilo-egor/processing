package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class WatOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.WAT, Merchant.WAT_PDF, Merchant.WAT_SIM
        );
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "AWAITING_PAYMENT" -> OrderStatus.NEW;
                    case "FINISHED" -> OrderStatus.SUCCESS;
                    case "CANCELED" -> OrderStatus.CANCELED;
                    case "CLAIM" -> OrderStatus.DISPUTE;
                    default -> null;
                }
        );
    }
}
