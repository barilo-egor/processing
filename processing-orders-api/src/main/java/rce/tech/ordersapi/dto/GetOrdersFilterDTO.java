package rce.tech.ordersapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record GetOrdersFilterDTO(
        @Valid
        @NotNull
        PaginationParamsDTO pagination,

        UUID id,
        List<Long> clientIds,
        String internalId,
        List<String> statuses,
        Integer minAmount,
        Integer maxAmount,
        Instant createdAtFrom,
        Instant createdAtTo
) {

    public GetOrdersFilterDTO {
        if (clientIds == null)
            clientIds = List.of();
        if (statuses == null)
            statuses = List.of();
    }

}
