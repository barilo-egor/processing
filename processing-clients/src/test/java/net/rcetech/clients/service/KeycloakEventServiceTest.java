package net.rcetech.clients.service;

import io.micrometer.core.instrument.MeterRegistry;
import net.rcetech.clients.event.KeycloakEvent;
import net.rcetech.clients.event.KeycloakEventHandler;
import net.rcetech.meta.MetricsConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeycloakEventServiceTest {

    @Mock
    private Executor executor;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private KeycloakEventHandler keycloakEventHandler;

    private KeycloakEventService keycloakEventService;

    @Test
    void handle_shouldHandleEvent() {
        when(keycloakEventHandler.getEventType()).thenReturn(KeycloakEvent.EventType.REGISTER);
        keycloakEventService = new KeycloakEventService(executor, List.of(keycloakEventHandler), meterRegistry);
        KeycloakEvent keycloakEvent = mock(KeycloakEvent.class);
        when(keycloakEvent.type()).thenReturn(KeycloakEvent.EventType.REGISTER);
        keycloakEventService.handle(keycloakEvent);
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(captor.capture());
        captor.getValue().run();
        verify(keycloakEventHandler).handle(keycloakEvent);
    }

    @Test
    void handle_shouldCountErrorIfHandlerThrowsException() {
        when(keycloakEventHandler.getEventType()).thenReturn(KeycloakEvent.EventType.REGISTER);
        keycloakEventService = new KeycloakEventService(executor, List.of(keycloakEventHandler), meterRegistry);
        KeycloakEvent keycloakEvent = mock(KeycloakEvent.class);
        when(keycloakEvent.type()).thenReturn(KeycloakEvent.EventType.REGISTER);
        doThrow(RuntimeException.class).when(keycloakEventHandler).handle(keycloakEvent);
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        keycloakEventService.handle(keycloakEvent);
        verify(executor).execute(captor.capture());
        captor.getValue().run();
        verify(meterRegistry).counter(
                MetricsConstants.KEYCLOAK_EVENT_HANDLE_ERROR,
                MetricsConstants.Tags.TYPE,
                KeycloakEvent.EventType.REGISTER.name()
        );
    }

    @Test
    void handle_shouldSkipIfNoHandler() {
        when(keycloakEventHandler.getEventType()).thenReturn(KeycloakEvent.EventType.REGISTER);
        keycloakEventService = new KeycloakEventService(executor, List.of(keycloakEventHandler), meterRegistry);
        KeycloakEvent keycloakEvent = mock(KeycloakEvent.class);
        when(keycloakEvent.type()).thenReturn(KeycloakEvent.EventType.CUSTOM_REQUIRED_ACTION_ERROR);
        keycloakEventService.handle(keycloakEvent);
        verify(keycloakEventHandler, never()).handle(keycloakEvent);
    }

    @Test
    void constructor_ShouldThrowNPEIfEventTypeIsNull() {
        when(keycloakEventHandler.getEventType()).thenReturn(null);
        List<KeycloakEventHandler> handlers = List.of(keycloakEventHandler);
        assertThrows(NullPointerException.class, () -> new KeycloakEventService(executor, handlers, meterRegistry));
    }

    @Test
    void constructor_shouldThrowIllegalStateExceptionIfDuplicatesHandlers() {
        when(keycloakEventHandler.getEventType()).thenReturn(KeycloakEvent.EventType.REGISTER);
        List<KeycloakEventHandler> handlers = List.of(keycloakEventHandler, keycloakEventHandler);
        assertThrows(IllegalStateException.class,
                () -> new KeycloakEventService(executor, handlers, meterRegistry)
        );
    }
}