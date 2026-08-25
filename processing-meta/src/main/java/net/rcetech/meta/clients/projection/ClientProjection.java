package net.rcetech.meta.clients.projection;

import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.meta.serialize.InstantToMillisSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

import java.time.Instant;
import java.util.UUID;

public interface ClientProjection {
    UUID getId();
    String getUsername();
    @JsonSerialize(using = InstantToMillisSerializer.class)
    Instant getRegisteredAt();
    ClientStatus getStatus();
    String getCallbackUrl();
    Integer getOrderTimeoutSeconds();
}
