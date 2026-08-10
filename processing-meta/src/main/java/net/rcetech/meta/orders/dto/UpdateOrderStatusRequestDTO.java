package net.rcetech.meta.orders.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record UpdateOrderStatusRequestDTO(
        @NotNull(message = "ID заказа обязателен")
        UUID id,

        @NotBlank(message = "Статус обязателен")
        @Pattern(regexp = "^[A-Z_]+$", message = "Статус должен быть в верхнем регистре")
        String status,

        @Min(value = 1, message = "Client ID должен быть больше 0")
        Long clientId
) {

}
