package net.rcetech.incidents.handler;

import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.meta.incidents.ClientIncidentEvent;
import net.rcetech.meta.incidents.IncidentEvent;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class ClientIncidentHandler implements IncidentHandler {

    private final IncidentService incidentService;

    public ClientIncidentHandler(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @Override
    public boolean supports(IncidentEvent event) {
        return event instanceof ClientIncidentEvent;
    }

    @Override
    public void handle(IncidentEvent event) {
        ClientIncidentEvent clientEvent = (ClientIncidentEvent) event;
        if (event.onlyIfNoUnresolved()) {
            incidentService.saveIfNoUnresolvedForClient(event.type(), clientEvent.clientId(), event,
                    event.errorTimestamp());
        } else {
            incidentService.save(event.type(), event, event.errorTimestamp());
        }
    }
}
