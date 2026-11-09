package net.rcetech.meta.support.dto;

import jakarta.validation.constraints.Positive;

public record MerchantConfigUpdateDTO(
        Boolean isOn,
        @Positive(message = "должно быть больше нуля")
        Integer maxAmount,
        @Positive(message = "должно быть больше нуля")
        Integer minAmount,
        @Positive(message = "должно быть больше нуля")
        Integer merchantOrder,
        Boolean swap
) {}
