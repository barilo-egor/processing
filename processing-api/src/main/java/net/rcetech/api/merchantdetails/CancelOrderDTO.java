package net.rcetech.api.merchantdetails;

import net.rcetech.meta.orders.RequestMethod;
import tgb.cryptoexchange.commons.enums.Merchant;

public record CancelOrderDTO(Merchant merchant, String merchantOrderId, RequestMethod method) {

}
