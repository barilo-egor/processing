package net.rcetech.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import net.rcetech.meta.orders.RequestMethod;
import org.hibernate.validator.constraints.URL;

import java.util.Set;

/**
 * Запрос на получение реквизитов от апи клиента.
 *
 * @param internalId идентификатор ордера внутри системы апи клиента
 * @param amount сумма ордера
 * @param methods методы оплаты
 * @param callbackUrl адрес для колл-бэков
 * @param userId идентификатор конечного пользователя, который будет совершать оплату
 */
public record CreateOrderRequest(
        @NotBlank(message = "должно быть заполнено") String internalId,
        @NotNull(message = "должно быть заполнено")
        @Positive(message = "должно быть больше нуля") Integer amount,
        @NotEmpty(message = "должно содержать хотя бы одно значение") Set<RequestMethod> methods,
        @URL(protocol = "https", message = "должно быть корректным адресом с протоколом https")
        String callbackUrl,
        String userId
) {}
