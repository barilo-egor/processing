package rce.tech.ordersapi.dto;

import java.util.List;

public record OrdersPageResponseDTO(List<OrderResponseDTO> orders, long totalElements) {

    public OrdersPageResponseDTO {
        if (orders == null) {
            orders = List.of();
        }
    }

}
