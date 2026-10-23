package net.rcetech.meta.incidents;

import java.util.UUID;

public interface OrderIncidentEvent extends IncidentEvent {

    UUID orderId();
}
