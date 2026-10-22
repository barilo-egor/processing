package net.rcetech.meta.support.dto;

import net.rcetech.meta.serialize.InstantToMillisSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

import java.time.Instant;
import java.util.UUID;

public record SupportUserResponse(
        UUID id,
        String username,
        @JsonSerialize(using = InstantToMillisSerializer.class)
        Instant registeredAt
) {}
