package net.rcetech.meta.keycloak;

public interface KeycloakEventHandler {

    void handle(KeycloakEvent event);

    KeycloakEvent.EventType getEventType();
}
