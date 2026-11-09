package net.rcetech.meta.clients;

import java.util.UUID;

/**
 * Поддержка заблокировала или разблокировала клиента.
 *
 * @param clientId идентификатор клиента
 * @param username логин клиента
 * @param blocked  {@code true}, если клиент заблокирован
 */
public record ClientBlockChangedEvent(
        UUID clientId,
        String username,
        boolean blocked
) {}
