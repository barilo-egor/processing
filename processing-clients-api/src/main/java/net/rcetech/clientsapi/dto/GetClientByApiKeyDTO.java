package net.rcetech.clientsapi.dto;

import jakarta.validation.constraints.NotBlank;

public record GetClientByApiKeyDTO(
        @NotBlank(message = "API key mandatory")
        String apiKey
) {

}
