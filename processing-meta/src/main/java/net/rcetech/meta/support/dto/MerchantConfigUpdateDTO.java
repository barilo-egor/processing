package net.rcetech.meta.support.dto;

public record MerchantConfigUpdateDTO(
        Boolean isOn,
        Integer maxAmount,
        Integer minAmount
) {}
