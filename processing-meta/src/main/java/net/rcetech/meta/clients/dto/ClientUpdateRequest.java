package net.rcetech.meta.clients.dto;

import net.rcetech.meta.orders.validation.CallbackUrl;

public record ClientUpdateRequest (
        @CallbackUrl
        String callbackUrl
){}
