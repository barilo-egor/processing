package net.rcetech.security;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.meta.clients.ClientDeletedEvent;
import net.rcetech.meta.exception.ServiceException;
import net.rcetech.security.keycloak.KeycloakAdminClient;
import net.rcetech.security.session.UserSessionRemover;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ClientDeletedEventListener {

    private final UserSessionRemover userSessionRemover;

    private final KeycloakAdminClient keycloakAdminClient;

    public ClientDeletedEventListener(UserSessionRemover userSessionRemover, KeycloakAdminClient keycloakAdminClient) {
        this.userSessionRemover = userSessionRemover;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    @EventListener
    public void logout(ClientDeletedEvent event) {
        int sessions = userSessionRemover.removeAll(event.clientId().toString());
        try {
            keycloakAdminClient.logout(event.clientId());
        } catch (Exception e) {
            throw new ServiceException("Не удалось завершить сессии клиента в Keycloak.", e);
        }
        log.info("Клиент {} разлогинен: удалено сессий приложения {}, сессии Keycloak завершены.",
                event.clientId(), sessions);
    }
}
