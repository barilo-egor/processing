package net.rcetech.meta.orders.dto;

import java.util.List;

public record OrdersPageResponseDTO(List<OrderSummary> orders, long totalElements) {

    public OrdersPageResponseDTO {
        if (orders == null) {
            orders = List.of();
        }
    }

}
