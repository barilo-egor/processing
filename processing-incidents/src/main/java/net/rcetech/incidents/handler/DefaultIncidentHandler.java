package net.rcetech.incidents.handler;

import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.meta.incidents.IncidentEvent;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order
public class DefaultIncidentHandler implements IncidentHandler {

    private final IncidentService incidentService;

    public DefaultIncidentHandler(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @Override
    public boolean supports(IncidentEvent event) {
        return true;
    }

    @Override
    public void handle(IncidentEvent event) {
        if (event.onlyIfNoUnresolved()) {
            incidentService.saveIfNoUnresolved(event.type(), event, event.errorTimestamp());
        } else {
            incidentService.save(event.type(), event, event.errorTimestamp());
        }
    }
}
