package net.rcetech.meta.clients;

import java.util.UUID;

public record ClientDeletedEvent(
        UUID clientId
) {}
