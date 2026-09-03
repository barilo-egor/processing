package net.rcetech.meta.orders.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.URL;

import java.util.UUID;

public record CreateOrderRequestDTO(
        @NotNull(message = "ID заказа обязателен")
        UUID id,

        @NotNull(message = "Client ID обязателен")
        @Min(value = 1, message = "Client ID должен быть больше 0")
        Long clientId,

        @NotBlank(message = "Internal ID обязателен")
        String internalId,

        @NotBlank(message = "Merchant обязателен")
        @Pattern(regexp = "^[A-Z_]+$", message = "Merchant должен состоять из прописных букв и подчеркиваний")
        String merchant,

        @NotBlank(message = "Merchant Order ID обязателен")
        String merchantOrderId,

        @NotBlank(message = "Merchant Order Status обязателен")
        @Pattern(regexp = "^[A-Z_]+$", message = "Merchant Order Status должен быть в верхнем регистре")
        String merchantOrderStatus,

        @NotNull(message = "Сумма обязательна")
        @Min(value = 1, message = "Сумма должна быть больше 0")
        Integer amount,

        Boolean enableUniqueAmount,

        @URL(message = "Некорректный формат URL")
        String callbackUrl
) {

}
