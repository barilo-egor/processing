package net.rcetech.meta.incidents;

import java.util.UUID;

public interface ClientIncidentEvent extends IncidentEvent {

    UUID clientId();
}
