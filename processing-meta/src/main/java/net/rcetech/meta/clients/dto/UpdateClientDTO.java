package net.rcetech.meta.clients.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import net.rcetech.meta.orders.RequestMethod;

import java.math.BigDecimal;
import java.util.Set;

public record UpdateClientDTO(
        @DecimalMin(value = "0", message = "должно быть больше или равно нулю")
        @DecimalMax(value = "100", inclusive = false, message = "должно быть меньше 100")
        BigDecimal commissionPercent,
        @Positive(message = "должно быть больше нуля") Integer orderTimeoutSeconds,
        Set<RequestMethod> methods,
        @Size(max = 1000, message = "должно содержать не более 1000 символов") String comment,
        @Positive(message = "должно быть больше нуля") Integer minWithdrawalAmount,
        Boolean ipRestrictionEnabled
) {}
