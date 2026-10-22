package net.rcetech.meta.billing;

import java.util.UUID;

public record TransactionCreatedEvent(
        UUID transactionId,
        UUID clientId
) {}
