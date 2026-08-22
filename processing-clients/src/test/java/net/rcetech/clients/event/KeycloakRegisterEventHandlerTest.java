package net.rcetech.clients.event;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.clients.ClientStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeycloakRegisterEventHandlerTest {

    @Mock
    private ClientService clientService;

    @InjectMocks
    private KeycloakRegisterEventHandler keycloakRegisterEventHandler;

    @Test
    void getEventTypeShouldReturnRegister() {
        assertEquals(KeycloakEvent.EventType.REGISTER, keycloakRegisterEventHandler.getEventType());
    }

    @CsvSource({"""
            c59837f0-6b03-4b17-a1f1-db0ebb4f441b,1787396725490,test1
            4b06fa5a-0bdd-4f7e-8cfa-0df665f1f903,1787396737394,admin365
            """})
    @ParameterizedTest
    void handleShouldBuildClientFromEvent(UUID id, long time, String username) {
        KeycloakEvent keycloakEvent = mock(KeycloakEvent.class);
        when(keycloakEvent.id()).thenReturn(id);
        when(keycloakEvent.time()).thenReturn(time);
        KeycloakEvent.Details details = mock(KeycloakEvent.Details.class);
        when(details.username()).thenReturn(username);
        when(keycloakEvent.details()).thenReturn(details);
        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        keycloakRegisterEventHandler.handle(keycloakEvent);
        verify(clientService).save(captor.capture());
        Client actual = captor.getValue();
        assertAll(
                () -> assertEquals(id, actual.getId()),
                () -> assertEquals(time, actual.getRegisteredAt().toEpochMilli()),
                () -> assertEquals(ClientStatus.ACTIVE, actual.getStatus()),
                () -> assertEquals(username, actual.getUsername())
        );
    }
}