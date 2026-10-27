package net.rcetech.meta.incidents;

public record IncidentCreatedEvent(
        Long id,
        IncidentType type
) {}
