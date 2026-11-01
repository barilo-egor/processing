package net.rcetech.meta.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import net.rcetech.meta.billing.Operation;

import java.util.UUID;

public record ManualCorrectTransaction(
        @NotNull(message = "должно быть заполнено")
        UUID clientId,
        @NotNull(message = "должно быть заполнено")
        Operation operation,
        @NotNull(message = "должно быть заполнено")
        @Positive(message = "должно быть больше нуля")
        Integer amount,
        @NotBlank(message = "должно быть заполнено")
        @Size(max = 500, message = "должно содержать не более 500 символов")
        String comment
) {}
