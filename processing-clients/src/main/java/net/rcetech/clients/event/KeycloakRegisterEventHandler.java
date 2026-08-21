package net.rcetech.clients.event;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.clients.ClientStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class KeycloakRegisterEventHandler implements KeycloakEventHandler {

    private final ClientService clientService;

    public KeycloakRegisterEventHandler(ClientService clientService) {
        this.clientService = clientService;
    }

    @Override
    public void handle(KeycloakEvent event) {
        Client client = new Client();
        client.setId(event.id());
        client.setRegisteredAt(Instant.ofEpochMilli(event.time()));
        client.setStatus(ClientStatus.ACTIVE);
        client.setUsername(event.details().username());
        clientService.save(client);
    }

    @Override
    public KeycloakEvent.EventType getEventType() {
        return KeycloakEvent.EventType.REGISTER;
    }
}
