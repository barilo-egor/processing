package net.rcetech.security;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.meta.clients.ClientBlockChangedEvent;
import net.rcetech.meta.exception.InternalServerErrorException;
import net.rcetech.meta.exception.ServiceException;
import net.rcetech.meta.incidents.ClientKeycloakBlockErrorEvent;
import net.rcetech.security.keycloak.KeycloakAdminClient;
import net.rcetech.security.session.UserSessionRemover;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Переносит блокировку клиента в Keycloak: у заблокированного выключает пользователя
 * и завершает все сессии, у разблокированного включает пользователя обратно.
 * Если заблокировать пользователя в Keycloak не удалось, сессии приложения всё равно удаляются
 * и создаётся инцидент на ручную блокировку.
 */
@Slf4j
@Component
public class ClientBlockChangedEventListener {

    static final String KEYCLOAK_BLOCK_ERROR = "Клиент заблокирован только в приложении: заблокировать его " +
            "в Keycloak не удалось. Создан инцидент «Клиент не заблокирован в Keycloak», который необходимо " +
            "обработать.";

    private final UserSessionRemover userSessionRemover;

    private final KeycloakAdminClient keycloakAdminClient;

    private final ApplicationEventPublisher eventPublisher;

    public ClientBlockChangedEventListener(UserSessionRemover userSessionRemover,
                                           KeycloakAdminClient keycloakAdminClient,
                                           ApplicationEventPublisher eventPublisher) {
        this.userSessionRemover = userSessionRemover;
        this.keycloakAdminClient = keycloakAdminClient;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void blockChanged(ClientBlockChangedEvent event) {
        if (event.blocked()) {
            block(event);
        } else {
            unblock(event);
        }
    }

    private void block(ClientBlockChangedEvent event) {
        try {
            keycloakAdminClient.setEnabled(event.clientId(), false);
            keycloakAdminClient.logout(event.clientId());
        } catch (Exception e) {
            int sessions = userSessionRemover.removeAll(event.clientId().toString());
            long timestamp = System.currentTimeMillis();
            log.error("{} Клиент {} заблокирован только в приложении, удалено сессий приложения {}: " +
                    "не удалось заблокировать его в Keycloak: {}", timestamp, event.clientId(), sessions,
                    e.getMessage(), e);
            eventPublisher.publishEvent(new ClientKeycloakBlockErrorEvent(event.clientId(), event.username(),
                    e.getMessage(), timestamp));
            throw new InternalServerErrorException(KEYCLOAK_BLOCK_ERROR, e);
        }
        int sessions = userSessionRemover.removeAll(event.clientId().toString());
        log.info("Клиент {} выключен в Keycloak: удалено сессий приложения {}, сессии Keycloak завершены.",
                event.clientId(), sessions);
    }

    private void unblock(ClientBlockChangedEvent event) {
        try {
            keycloakAdminClient.setEnabled(event.clientId(), true);
        } catch (Exception e) {
            throw new ServiceException("Не удалось разблокировать клиента в Keycloak.", e);
        }
        log.info("Клиент {} включён в Keycloak.", event.clientId());
    }
}
