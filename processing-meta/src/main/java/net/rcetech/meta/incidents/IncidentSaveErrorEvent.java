package net.rcetech.meta.incidents;

public record IncidentSaveErrorEvent(
        IncidentType type,
        Object data,
        long errorTimestamp
) {}
