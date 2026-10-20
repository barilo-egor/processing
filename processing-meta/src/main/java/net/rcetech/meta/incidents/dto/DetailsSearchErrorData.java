package net.rcetech.meta.incidents.dto;

import java.util.UUID;

public record DetailsSearchErrorData(
        UUID orderId,
        String error
) {}
