package net.rcetech.meta.exception;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ExceptionMessage {
    public static final String INTERNAL_ID_NOT_UNIQUE = "Внутренний идентификатор(internalId) должен быть уникальным, " +
            "ордер с таким идентификатором уже присутствует в системе.";
}
