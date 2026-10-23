package net.rcetech.incidents.handler;

import net.rcetech.meta.incidents.IncidentEvent;

public interface IncidentHandler {

    boolean supports(IncidentEvent event);

    void handle(IncidentEvent event);
}
