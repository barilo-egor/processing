package net.rcetech.clientsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateWithdrawalRequestDTO(
        @NotNull(message = "Client ID mandatory")
        @Positive(message = "Client ID must be positive")
        Long clientId,

        @NotNull(message = "Amount mandatory")
        @Positive(message = "Amount must be greater than zero")
        Integer amount,

        @NotBlank(message = "Wallet mandatory")
        String wallet,

        String comment
) {

}
