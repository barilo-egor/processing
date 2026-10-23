package net.rcetech.meta.orders;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ClientCallbackStatus {
    PENDING("Ожидает отправки"),
    SENDING("Отправляется"),
    SENT("Отправлен"),
    FAILED("Не доставлен");

    private final String description;
}
