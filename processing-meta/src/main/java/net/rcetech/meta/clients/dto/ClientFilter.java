package net.rcetech.meta.clients.dto;

import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.meta.serialize.MillisToInstantDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;
import java.util.UUID;

/**
 * Форма фильтра клиентов в админ панели.
 */
public record ClientFilter(
        UUID id,
        String username,
        ClientStatus status,
        @JsonDeserialize(using = MillisToInstantDeserializer.class)
        Instant from,
        @JsonDeserialize(using = MillisToInstantDeserializer.class)
        Instant to
) {
}
