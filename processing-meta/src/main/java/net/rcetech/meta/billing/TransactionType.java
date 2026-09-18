package net.rcetech.meta.billing;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TransactionType {
    ORDER_CONFIRMATION("Подтверждение ордера"),
    CLIENT_WITHDRAWAL("Вывод по инициативе клиента"),
    MANUAL_CORRECT("Ручная корректировка");

    private final String description;

}
