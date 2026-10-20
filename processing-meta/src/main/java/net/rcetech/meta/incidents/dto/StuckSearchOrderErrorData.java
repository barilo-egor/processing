package net.rcetech.meta.incidents.dto;

import java.util.UUID;

public record StuckSearchOrderErrorData(
        UUID orderId,
        String error
) {}
