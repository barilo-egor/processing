package net.rcetech.api.dto;

import net.rcetech.meta.orders.RequestMethod;
import tgb.cryptoexchange.commons.enums.Merchant;

/**
 * Ответ от merchant-details на получение реквизитов
 *
 * @param requestId идентификатор запроса реквизитов
 * @param merchant мерчант, выдавший реквизиты
 * @param orderId идентификатор ордера в системе мерчанта
 * @param orderStatus статус ордера в системе мерчанта
 * @param amount
 * @param details
 */
public record ApiDetailsResponse(
        String requestId,
        Merchant merchant,
        String orderId,
        String orderStatus,
        Integer amount,
        Details details
) {
    public record Details(
            RequestMethod requestMethod,
            String details,
            String bank
    ) {}
}
