package net.rcetech.meta.billing.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record GetTransactionsResponse(
        List<TransactionDTO> transactions,
        long totalElements
) {}
