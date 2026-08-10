package net.rcetech.meta.clients.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateClientDTO(
        @NotBlank(message = "Username cannot be empty")
        String username,

        @NotBlank(message = "Password cannot be empty")
        String password
) {

}
