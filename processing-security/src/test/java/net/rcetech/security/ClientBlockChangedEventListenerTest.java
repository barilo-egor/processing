package net.rcetech.security;

import net.rcetech.meta.clients.ClientBlockChangedEvent;
import net.rcetech.meta.exception.InternalServerErrorException;
import net.rcetech.meta.exception.ServiceException;
import net.rcetech.meta.incidents.ClientKeycloakBlockErrorEvent;
import net.rcetech.security.keycloak.KeycloakAdminClient;
import net.rcetech.security.session.UserSessionRemover;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientBlockChangedEventListenerTest {

    private static final String INCIDENT_MESSAGE = "Клиент заблокирован только в приложении: заблокировать его " +
            "в Keycloak не удалось. Создан инцидент «Клиент не заблокирован в Keycloak», который необходимо " +
            "обработать.";

    @Mock
    private UserSessionRemover userSessionRemover;

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ClientBlockChangedEventListener listener;

    @ValueSource(strings = {
            "6f1d2c3b-4a5e-4f60-8b7c-9d0e1f2a3b4c",
            "0a9b8c7d-6e5f-4a3b-9c1d-2e3f4a5b6c7d"
    })
    @ParameterizedTest
    @DisplayName("Слушатель должен выключить пользователя в Keycloak, завершить его сессии и удалить сессии приложения.")
    void blockChanged_shouldDisableUserAndRemoveSessionsIfBlocked(UUID clientId) {
        listener.blockChanged(new ClientBlockChangedEvent(clientId, "client", true));

        InOrder inOrder = inOrder(keycloakAdminClient, userSessionRemover);
        inOrder.verify(keycloakAdminClient).setEnabled(clientId, false);
        inOrder.verify(keycloakAdminClient).logout(clientId);
        inOrder.verify(userSessionRemover).removeAll(clientId.toString());
        verifyNoInteractions(eventPublisher);
    }

    @ValueSource(strings = {
            "6f1d2c3b-4a5e-4f60-8b7c-9d0e1f2a3b4c",
            "0a9b8c7d-6e5f-4a3b-9c1d-2e3f4a5b6c7d"
    })
    @ParameterizedTest
    @DisplayName("Слушатель должен включить пользователя в Keycloak и не трогать сессии, если клиент разблокирован.")
    void blockChanged_shouldEnableUserIfUnblocked(UUID clientId) {
        listener.blockChanged(new ClientBlockChangedEvent(clientId, "client", false));

        verify(keycloakAdminClient).setEnabled(clientId, true);
        verify(keycloakAdminClient, never()).logout(any());
        verifyNoInteractions(userSessionRemover, eventPublisher);
    }

    @ValueSource(strings = {"Connection refused", "500 Internal Server Error"})
    @ParameterizedTest
    @DisplayName("Слушатель должен удалить сессии приложения, создать инцидент и выбросить InternalServerErrorException, " +
            "если Keycloak не выключил пользователя.")
    void blockChanged_shouldCreateIncidentIfSetEnabledFailed(String error) {
        UUID clientId = UUID.randomUUID();
        doThrow(new RestClientException(error)).when(keycloakAdminClient).setEnabled(clientId, false);

        InternalServerErrorException exception = assertThrows(InternalServerErrorException.class,
                () -> listener.blockChanged(new ClientBlockChangedEvent(clientId, "user_poser", true)));

        assertEquals(INCIDENT_MESSAGE, exception.getMessage());
        verify(keycloakAdminClient, never()).logout(any());
        verify(userSessionRemover).removeAll(clientId.toString());
        assertIncident(clientId, "user_poser", error);
    }

    @ValueSource(strings = {"Connection refused", "500 Internal Server Error"})
    @ParameterizedTest
    @DisplayName("Слушатель должен удалить сессии приложения, создать инцидент и выбросить InternalServerErrorException, " +
            "если Keycloak не завершил сессии.")
    void blockChanged_shouldCreateIncidentIfLogoutFailed(String error) {
        UUID clientId = UUID.randomUUID();
        doThrow(new RestClientException(error)).when(keycloakAdminClient).logout(clientId);

        InternalServerErrorException exception = assertThrows(InternalServerErrorException.class,
                () -> listener.blockChanged(new ClientBlockChangedEvent(clientId, "client", true)));

        assertEquals(INCIDENT_MESSAGE, exception.getMessage());
        verify(userSessionRemover).removeAll(clientId.toString());
        assertIncident(clientId, "client", error);
    }

    @Test
    @DisplayName("Слушатель должен выбросить ServiceException без инцидента, если Keycloak не включил пользователя.")
    void blockChanged_shouldThrowIfUnblockFailed() {
        UUID clientId = UUID.randomUUID();
        doThrow(new RestClientException("500 Internal Server Error"))
                .when(keycloakAdminClient).setEnabled(any(), anyBoolean());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> listener.blockChanged(new ClientBlockChangedEvent(clientId, "client", false)));

        assertEquals("Не удалось разблокировать клиента в Keycloak.", exception.getMessage());
        verifyNoInteractions(userSessionRemover, eventPublisher);
    }

    private void assertIncident(UUID clientId, String username, String error) {
        ArgumentCaptor<ClientKeycloakBlockErrorEvent> captor = ArgumentCaptor.forClass(ClientKeycloakBlockErrorEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ClientKeycloakBlockErrorEvent incident = captor.getValue();
        assertAll(
                () -> assertEquals(clientId, incident.clientId()),
                () -> assertEquals(username, incident.username()),
                () -> assertEquals(error, incident.error()),
                () -> assertTrue(incident.errorTimestamp() > 0)
        );
    }
}
