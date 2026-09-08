package net.rcetech.meta.orders.dto;

import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.serialize.InstantToSystemLocalDateTimeSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

import java.time.Instant;
import java.util.UUID;

/**
 * Представление ордера доступное пользователю
 * @param id идентификатор ордера
 * @param createdAt время создания ордера
 * @param internalId идентификатор ордера в системе клиента
 * @param status статус ордера
 * @param amount сумма ордера
 * @param enableUniqueAmount признак того, была ли разрешена уникализация клиентом
 * @param callbackUrl адрес отправки колл-бэков
 */
public record OrderSummary(
        UUID id,
        @JsonSerialize(using = InstantToSystemLocalDateTimeSerializer.class)
        Instant createdAt,
        String internalId,
        OrderStatus status,
        Integer amount,
        boolean enableUniqueAmount,
        String callbackUrl
) {}
