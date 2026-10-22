package net.rcetech.meta.orders;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum OrderStatus {
    SEARCHING("Поиск реквизитов"),
    DETAILS_NOT_FOUND("Реквизиты не найдены"),
    NEW("Новый"),
    CANCELED("Отменен"),
    TIMEOUT("Просрочен"),
    DISPUTE("В споре"),
    SUCCESS("Успешный");

    private final String description;
}
