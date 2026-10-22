package net.rcetech.incidents;

import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.meta.incidents.IncidentEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class IncidentEventListener {

    private final IncidentService incidentService;

    public IncidentEventListener(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @EventListener
    public void incident(IncidentEvent event) {
        if (event.onlyIfNoUnresolved()) {
            incidentService.saveIfNoUnresolved(event.type(), event, event.errorTimestamp());
        } else {
            incidentService.save(event.type(), event, event.errorTimestamp());
        }
    }
}
