package net.rcetech.meta.incidents.dto;

import net.rcetech.meta.incidents.IncidentStatus;
import net.rcetech.meta.incidents.IncidentType;

import java.time.Instant;

public record IncidentFilter(
        Long id,
        Instant createdAtFrom,
        Instant createdAtTo,
        IncidentStatus status,
        IncidentType type,
        Instant resolvedAtFrom,
        Instant resolvedAtTo
) {}
