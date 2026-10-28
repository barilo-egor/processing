package net.rcetech.meta.clients.dto;

import net.rcetech.meta.orders.validation.CallbackUrl;

import java.util.Optional;

/**
 * Изменение профиля клиентом.
 *
 * @param callbackUrl адрес КБ: {@code null} — не менять, пустой {@link Optional} — удалить адрес
 */
public record ClientUpdateRequest(
        Optional<@CallbackUrl String> callbackUrl
) {}
