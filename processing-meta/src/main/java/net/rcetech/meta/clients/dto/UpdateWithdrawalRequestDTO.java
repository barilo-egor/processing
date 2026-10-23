package net.rcetech.meta.clients.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateWithdrawalRequestDTO(
        @NotNull(message = "должно быть заполнено")
        @Positive(message = "должно быть больше нуля")
        Long id,

        String wallet,

        String comment
) {

}
