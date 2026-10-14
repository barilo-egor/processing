package net.rcetech.meta.billing;

import java.util.UUID;

public record TransactionCreatedEvent(
        Long transactionId,
        UUID clientId
) {}
