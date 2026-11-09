package net.rcetech.meta.incidents;

import java.util.UUID;

/**
 * Клиент заблокирован в приложении, но выключить его в Keycloak и завершить там его сессии не удалось.
 *
 * @param clientId       идентификатор клиента
 * @param username       логин клиента
 * @param error          текст ошибки Keycloak
 * @param errorTimestamp момент ошибки
 */
public record ClientKeycloakBlockErrorEvent(
        UUID clientId,
        String username,
        String error,
        long errorTimestamp
) implements ClientIncidentEvent {

    @Override
    public IncidentType type() {
        return IncidentType.CLIENT_KEYCLOAK_BLOCK_ERROR;
    }
}
