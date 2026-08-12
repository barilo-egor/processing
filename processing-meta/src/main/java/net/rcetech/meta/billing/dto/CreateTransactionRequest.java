package net.rcetech.meta.billing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

import java.util.UUID;

@Builder
public record CreateTransactionRequest(
        @NotNull
        UUID id,

        @NotNull
        @Min(value = 1, message = "Client ID must be greater than 0")
        Long clientId,

        @NotNull
        @Min(value = 1, message = "Amount must be greater than 0")
        Integer amount,

        @NotNull
        @Pattern(regexp = "^[A-Z_]+$", message = "Operation must match pattern ^[A-Z_]+$")
        String operation,

        @NotNull
        @Pattern(regexp = "^[A-Z_]+$", message = "Type must match pattern ^[A-Z_]+$")
        String type,

        String comment
) {}