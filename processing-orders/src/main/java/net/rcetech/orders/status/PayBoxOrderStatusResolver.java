package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class PayBoxOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.EXTASY_PAY, Merchant.EXTASY_PAY_QR, Merchant.EXTASY_PAY_RECEIPT, Merchant.EXTASY_PAY_RECEIPT_3,
                Merchant.HELLBIT, Merchant.HELLBIT_BT,
                Merchant.PRIME_WALLET, Merchant.PRIME_WALLET_LOW_CHECK, Merchant.PRIME_WALLET_HIGH_CHECK,
                Merchant.PW_PAY
        );
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "UNDERPAID", "OVERPAID", "PROCESS" -> OrderStatus.NEW;
                    case "PAID" -> OrderStatus.SUCCESS;
                    case "CANCEL", "ERROR" -> OrderStatus.CANCELED;
                    case "EXPIRED" -> OrderStatus.TIMEOUT;
                    default -> null;
                }
        );
    }
}
