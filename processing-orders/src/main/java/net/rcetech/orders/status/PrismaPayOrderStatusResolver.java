package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Component
public class PrismaPayOrderStatusResolver implements OrderStatusResolver {
    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(
                Merchant.PRISMA_PAY
        );
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "PENDING", "WAITING_ADMIN_APPROVAL" -> OrderStatus.NEW;
                    case "SUCCESS" -> OrderStatus.SUCCESS;
                    case "CANCELLED" -> OrderStatus.CANCELED;
                    case "EXPIRED" -> OrderStatus.TIMEOUT;
                    case "DISPUTE" -> OrderStatus.DISPUTE;
                    default -> null;
                }
        );
    }
}
