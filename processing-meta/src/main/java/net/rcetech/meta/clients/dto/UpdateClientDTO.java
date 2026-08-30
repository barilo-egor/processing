package net.rcetech.meta.clients.dto;

import jakarta.validation.constraints.Min;
import net.rcetech.meta.clients.ClientStatus;
import org.hibernate.validator.constraints.URL;

public record UpdateClientDTO(
        ClientStatus status,
        @Min(0) Integer orderTimeoutSeconds,
        @URL(protocol = "https") String callbackUrl
) {}
