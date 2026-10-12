package net.rcetech.api.merchantdetails;

import tgb.cryptoexchange.commons.enums.Merchant;

public record CancelOrderDTO(Merchant merchant, String merchantOrderId, String merchantMethod) {

}
