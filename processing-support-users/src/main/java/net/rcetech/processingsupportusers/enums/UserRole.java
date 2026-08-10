package net.rcetech.processingsupportusers.enums;

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
