package net.rcetech.meta.support;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum UserRole {

    NEW("Зарегистрирован"),
    OPERATOR("Оператор"),
    ADMINISTRATOR("Администратор");

    private final String description;

}
