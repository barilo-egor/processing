package net.rcetech.meta.billing;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum WithdrawalRequestStatus {
    /**
     * Создана клиентом
     */
    NEW("Новая"),
    /**
     * Подтверждена пользователем поддержки
     */
    APPROVED("Подтверждена"),
    /**
     * Отменена
     */
    CANCELED("Отменена");

    private final String description;
}
