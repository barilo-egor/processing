package net.rcetech.meta.orders.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Адрес КБ клиента: https, порт 443, не длиннее 2048 символов, хост — доменное имя публичного сервера,
 * без логина с паролем и фрагмента. {@code null} допустим.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CallbackUrlValidator.class)
public @interface CallbackUrl {

    String message() default "должно быть адресом с протоколом https и портом 443 не длиннее 2048 символов, "
            + "с доменным именем публичного сервера, без логина и пароля";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
