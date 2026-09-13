package net.rcetech.orders.status;

import net.rcetech.meta.orders.OrderStatus;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

public interface OrderStatusResolver {

    Set<Merchant> getMerchants();

    Optional<OrderStatus> resolve(String status);
}
