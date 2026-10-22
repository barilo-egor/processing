package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import org.springframework.stereotype.Service;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

@Service
public class LotrienOrderStatusResolver implements OrderStatusResolver {

    @Override
    public Set<Merchant> getMerchants() {
        return Set.of(Merchant.LOTRIEN, Merchant.LOTRIEN_PDF);
    }

    @Override
    public Optional<OrderStatus> resolve(String status) {
        return Optional.ofNullable(
                switch (status) {
                    case "CREATED", "PENDING", "IN_PROGRESS" -> OrderStatus.NEW;
                    case "SUCCESS" -> OrderStatus.SUCCESS;
                    case "UNISSUED", "CANCEL" -> OrderStatus.CANCELED;
                    case "EXPIRE" -> OrderStatus.TIMEOUT;
                    case "APPEAL" -> OrderStatus.DISPUTE;
                    default -> null;
                }
        );
    }
}
