package net.rcetech.meta.billing.dto;

import net.rcetech.meta.billing.WithdrawalRequestStatus;
import net.rcetech.meta.serialize.BigDecimalPlaintStringSerializer;
import net.rcetech.meta.serialize.InstantToMillisSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WithdrawalRequestResponse(
        UUID clientId,
        String clientUsername,
        UUID id,
        WithdrawalRequestStatus status,
        @JsonSerialize(using = InstantToMillisSerializer.class)
        Instant createdAt,
        Integer grossSourceAmount,
        @JsonSerialize(using = BigDecimalPlaintStringSerializer.class)
        BigDecimal commissionPercent,
        Integer netSourceAmount,
        BigDecimal rate,
        Integer targetAmount,
        String address
) {}
