package net.rcetech.clients.event;

public interface KeycloakEventHandler {

    void handle(KeycloakEvent event);

    KeycloakEvent.EventType getEventType();
}
