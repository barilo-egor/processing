package net.rcetech.meta.billing.dto;

import net.rcetech.meta.serialize.MillisToInstantDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;
import java.util.UUID;

public record ClientTransactionFilter(
        UUID id,
        @JsonDeserialize(using = MillisToInstantDeserializer.class)
        Instant createdAtFrom,
        @JsonDeserialize(using = MillisToInstantDeserializer.class)
        Instant createdAtTo
) {}
