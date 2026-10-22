package net.rcetech.security;

import net.rcetech.meta.clients.ClientDeletedEvent;
import net.rcetech.meta.exception.ServiceException;
import net.rcetech.security.keycloak.KeycloakAdminClient;
import net.rcetech.security.session.UserSessionRemover;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientDeletedEventListenerTest {

    @Mock
    private UserSessionRemover userSessionRemover;

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @InjectMocks
    private ClientDeletedEventListener listener;

    @ValueSource(strings = {
            "6f1d2c3b-4a5e-4f60-8b7c-9d0e1f2a3b4c",
            "0a9b8c7d-6e5f-4a3b-9c1d-2e3f4a5b6c7d"
    })
    @ParameterizedTest
    @DisplayName("Слушатель должен удалить сессии приложения и завершить сессии Keycloak удалённого клиента.")
    void logout_shouldRemoveSessionsAndLogoutFromKeycloak(UUID clientId) {
        listener.logout(new ClientDeletedEvent(clientId));

        InOrder inOrder = inOrder(userSessionRemover, keycloakAdminClient);
        inOrder.verify(userSessionRemover).removeAll(clientId.toString());
        inOrder.verify(keycloakAdminClient).logout(clientId);
    }

    @ValueSource(strings = {"Connection refused", "500 Internal Server Error"})
    @ParameterizedTest
    @DisplayName("Слушатель должен выбросить ServiceException, если Keycloak не завершил сессии.")
    void logout_shouldThrowServiceExceptionIfKeycloakFailed(String message) {
        UUID clientId = UUID.randomUUID();
        doThrow(new RestClientException(message)).when(keycloakAdminClient).logout(clientId);

        assertThrows(ServiceException.class, () -> listener.logout(new ClientDeletedEvent(clientId)));
    }
}
