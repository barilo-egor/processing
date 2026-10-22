package net.rcetech.meta.incidents.dto;

import net.rcetech.meta.incidents.IncidentStatus;
import net.rcetech.meta.incidents.IncidentType;
import net.rcetech.meta.serialize.InstantToMillisSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IncidentResponse(
        Long id,
        @JsonSerialize(using = InstantToMillisSerializer.class)
        Instant createdAt,
        IncidentStatus status,
        Long errorTimestamp,
        IncidentType type,
        Map<String, Object> data,
        UUID resolvedById,
        @JsonSerialize(using = InstantToMillisSerializer.class)
        Instant resolvedAt,
        String comment
) {}
