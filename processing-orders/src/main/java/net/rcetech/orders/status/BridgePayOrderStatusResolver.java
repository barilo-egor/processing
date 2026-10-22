package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class BridgePayOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.ALFA_TEAM, Merchant.ALFA_TEAM_QR, Merchant.ALFA_TEAM_WT,
                Merchant.DEORA, Merchant.DEORA_LOW_CHECK, Merchant.DEORA_PDF, Merchant.DEORA_SIM,
                Merchant.GEO_TRANSFER,
                Merchant.ONYX_PAY,
                Merchant.ROSTRAST,
                Merchant.SOUZ, Merchant.SOUZ_PDF, Merchant.SOUZ_SBP_QR, Merchant.SOUZ_SIM,
                Merchant.STORM_TRADE, Merchant.STORM_TRADE_13
        );
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "NEW" -> OrderStatus.NEW;
                    case "PAID" -> OrderStatus.SUCCESS;
                    case "CANCELED" -> OrderStatus.CANCELED;
                    case "EXPIRED" -> OrderStatus.TIMEOUT;
                    case "DISPUTE" -> OrderStatus.DISPUTE;
                    default -> null;
                }
        );
    }
}
