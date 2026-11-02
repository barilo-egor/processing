package net.rcetech.meta.orders;

import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.UUID;

/**
 * Событие получения от merchant-details реквизитов, не прошедших проверку processing.
 *
 * @param orderId         идентификатор ордера
 * @param clientId        идентификатор клиента
 * @param merchant        мерчант, выдавший реквизиты
 * @param merchantOrderId идентификатор ордера в системе мерчанта
 * @param method          метод оплаты из ответа merchant-details
 * @param reason          причина отклонения реквизитов
 * @param errorTimestamp  время отклонения, миллисекунды Unix-времени
 */
public record InvalidDetailsReceivedEvent(
        UUID orderId,
        UUID clientId,
        Merchant merchant,
        String merchantOrderId,
        RequestMethod method,
        String reason,
        long errorTimestamp
) {}
