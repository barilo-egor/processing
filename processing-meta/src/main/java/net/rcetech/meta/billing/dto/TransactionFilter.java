package net.rcetech.meta.billing.dto;

import net.rcetech.meta.serialize.MillisToInstantDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;

public record TransactionFilter(
        String client,
        @JsonDeserialize(using = MillisToInstantDeserializer.class)
        Instant createdAtFrom,
        @JsonDeserialize(using = MillisToInstantDeserializer.class)
        Instant createdAtTo
) {}