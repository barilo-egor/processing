package rce.tech.ordersapi.dto;

import jakarta.validation.constraints.Min;

import java.util.List;

public record PaginationParamsDTO(
        @Min(value = 0, message = "Номер страницы не может быть отрицательным")
        int page,

        @Min(value = 1, message = "Размер страницы должен быть больше 0")
        int size,

        List<String> sorters) {

    public PaginationParamsDTO {
        if (sorters == null) {
            sorters = List.of();
        }
    }

}
