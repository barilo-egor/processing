package net.rcetech.clientsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateSignatureDTO(
        @NotNull(message = "Client ID mandatory")
        @Positive(message = "Client ID must be positive")
        Long clientId,

        @NotBlank(message = "Data for signature mandatory")
        String data
) {

}
