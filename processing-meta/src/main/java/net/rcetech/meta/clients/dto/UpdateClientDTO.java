package net.rcetech.meta.clients.dto;

import jakarta.validation.constraints.Min;
import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.meta.orders.RequestMethod;

import java.math.BigDecimal;
import java.util.Set;

public record UpdateClientDTO(
        ClientStatus status,
        @Min(0) BigDecimal commissionPercent,
        @Min(0) Integer orderTimeoutSeconds,
        Set<RequestMethod> methods
) {}
