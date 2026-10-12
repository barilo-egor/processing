package net.rcetech.meta.clients.dto;

import org.hibernate.validator.constraints.URL;

public record ClientUpdateRequest (
        @URL(protocol = "https")
        String callbackUrl
){}
