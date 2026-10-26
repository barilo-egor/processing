package net.rcetech.meta.clients.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import net.rcetech.meta.orders.RequestMethod;

import java.math.BigDecimal;
import java.util.Set;

public record UpdateClientDTO(
        @Min(0) BigDecimal commissionPercent,
        @Positive Integer orderTimeoutSeconds,
        Set<RequestMethod> methods,
        String comment,
        Integer minWithdrawalAmount,
        Boolean ipRestrictionEnabled
) {}
