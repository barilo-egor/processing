package net.rcetech.billing.dto;

import lombok.Builder;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record GetTransactionsRequest(
        Integer page,
        Integer size,
        List<String> sorters,
        UUID id,
        List<Long> clientIds,
        Integer minAmount,
        Integer maxAmount,
        List<String> operations,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant createdAtFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant createdAtTo
) {
    public GetTransactionsRequest {
        if (page == null) page = 0;
        if (size == null) size = 20;
    }
}