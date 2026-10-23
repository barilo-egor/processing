package net.rcetech.incidents.handler;

import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.meta.incidents.IncidentEvent;
import net.rcetech.meta.incidents.OrderIncidentEvent;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class OrderIncidentHandler implements IncidentHandler {

    private final IncidentService incidentService;

    public OrderIncidentHandler(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @Override
    public boolean supports(IncidentEvent event) {
        return event instanceof OrderIncidentEvent;
    }

    @Override
    public void handle(IncidentEvent event) {
        OrderIncidentEvent orderEvent = (OrderIncidentEvent) event;
        if (event.onlyIfNoUnresolved()) {
            incidentService.saveIfNoUnresolvedForOrder(event.type(), orderEvent.orderId(), event,
                    event.errorTimestamp());
        } else {
            incidentService.save(event.type(), event, event.errorTimestamp());
        }
    }
}
