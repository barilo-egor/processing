package net.rcetech.meta.clients.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateWithdrawalRequestDTO(
        @NotNull(message = "должно быть заполнено")
        @Positive(message = "должно быть больше нуля")
        Long clientId,

        @NotNull(message = "должно быть заполнено")
        @Positive(message = "должно быть больше нуля")
        Integer amount,

        @NotBlank(message = "должно быть заполнено")
        String wallet,

        String comment
) {

}
