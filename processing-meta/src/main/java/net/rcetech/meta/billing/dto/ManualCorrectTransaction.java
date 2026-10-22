package net.rcetech.meta.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import net.rcetech.meta.billing.Operation;

import java.util.UUID;

public record ManualCorrectTransaction(
        @NotNull(message = "should not be null")
        UUID clientId,
        @NotNull(message = "should not be null")
        Operation operation,
        @NotNull(message = "should not be null")
        @PositiveOrZero(message = "should be positive or zero")
        Integer amount,
        @NotBlank(message = "should not be blank")
        String comment
) {}
