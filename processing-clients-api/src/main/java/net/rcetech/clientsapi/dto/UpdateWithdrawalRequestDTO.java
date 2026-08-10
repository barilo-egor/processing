package net.rcetech.clientsapi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateWithdrawalRequestDTO(
        @NotNull(message = "Withdrawal request ID mandatory")
        @Positive(message = "ID must be positive")
        Long id,

        String wallet,

        String comment
) {

}
