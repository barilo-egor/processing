package net.rcetech.meta.billing;

import java.util.UUID;

public record WithdrawalRequestCreatedEvent(
        UUID id,
        String username,
        Integer amount
) {}
