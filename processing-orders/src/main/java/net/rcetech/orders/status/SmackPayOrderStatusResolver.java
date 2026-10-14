package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class SmackPayOrderStatusResolver implements OrderStatusResolver{
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(Merchant.SMACK_PAY);
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "AWAITING_KYC", "KYC_PENDING", "PROCESSING_LINK", "PENDING" -> OrderStatus.NEW;
                    case "SUCCEEDED" -> OrderStatus.SUCCESS;
                    case "FAILED" -> OrderStatus.CANCELED;
                    case "EXPIRED" -> OrderStatus.TIMEOUT;
                    default -> null;
                }
        );
    }
}
