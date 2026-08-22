package net.rcetech.clients.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.clients.event.KeycloakEvent;
import net.rcetech.clients.event.KeycloakEventHandler;
import net.rcetech.meta.MetricsConstants;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class KeycloakEventService {

    private final Executor keycloakEventExecutor;

    private final Map<KeycloakEvent.EventType, KeycloakEventHandler>  keycloakEventHandlers;

    private final MeterRegistry meterRegistry;

    public KeycloakEventService(Executor keycloakEventExecutor, Collection<KeycloakEventHandler> keycloakEventHandlers, MeterRegistry meterRegistry) {
        this.keycloakEventExecutor = keycloakEventExecutor;
        this.meterRegistry = meterRegistry;
        this.keycloakEventHandlers = new EnumMap<>(KeycloakEvent.EventType.class);
        for (KeycloakEventHandler keycloakEventHandler : keycloakEventHandlers) {
            var result = this.keycloakEventHandlers.put(
                    Objects.requireNonNull(keycloakEventHandler.getEventType(),
                            "Обработчик ивента от keycloak должен иметь тип, сейчас null. " +
                            "Class.name=" + keycloakEventHandler.getClass().getName()),
                    keycloakEventHandler
            );
            if (result != null) {
                throw new IllegalStateException("Обнаружен дубликат обработчик ивента от keycloak типа "
                        + result.getEventType().name());
            }
        }
    }

    public void handle(KeycloakEvent event) {
        KeycloakEventHandler keycloakEventHandler = keycloakEventHandlers.get(event.type());
        if (Objects.nonNull(keycloakEventHandler)) {
            keycloakEventExecutor.execute(() -> {
                try {
                    keycloakEventHandler.handle(event);
                } catch (Exception e) {
                    log.error("Ошибка при попытке обработки keycloak ивента({}): {}", event, e.getMessage(), e);
                    meterRegistry.counter(MetricsConstants.KEYCLOAK_EVENT_HANDLE_ERROR, MetricsConstants.Tags.TYPE, event.type().name());
                }
            });
        } else {
            log.trace("Ивент от keycloak проигнорирован: {}", event);
        }
    }
}
