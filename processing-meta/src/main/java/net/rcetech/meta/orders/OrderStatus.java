package net.rcetech.meta.orders;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum OrderStatus {
    NEW("Новый"),
    CANCELED("Отменен"),
    TIMEOUT("Таймаут"),
    SUCCESS("Успешный");

    private final String description;
}
