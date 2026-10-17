package net.rcetech.meta.incidents.dto;

import java.util.UUID;

public record BalanceUpdateErrorData(
        UUID transactionId,
        UUID clientId
) {}
