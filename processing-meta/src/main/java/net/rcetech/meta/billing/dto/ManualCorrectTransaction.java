package net.rcetech.meta.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import net.rcetech.meta.billing.Operation;

import java.util.UUID;

public record ManualCorrectTransaction(
        @NotNull(message = "должно быть заполнено")
        UUID clientId,
        @NotNull(message = "должно быть заполнено")
        Operation operation,
        @NotNull(message = "должно быть заполнено")
        @PositiveOrZero(message = "должно быть больше или равно нулю")
        Integer amount,
        @NotBlank(message = "должно быть заполнено")
        String comment
) {}
