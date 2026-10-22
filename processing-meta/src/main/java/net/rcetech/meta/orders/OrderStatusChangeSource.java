package net.rcetech.meta.orders;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum OrderStatusChangeSource {
    MERCHANT_CALLBACK("КБ мерчанта"),
    CLIENT("Клиент"),
    SUPPORT("Поддержка"),
    SYSTEM("Система");

    private final String description;
}
