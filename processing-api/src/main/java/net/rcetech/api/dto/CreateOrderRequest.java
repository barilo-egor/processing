package net.rcetech.api.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
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
 * @param enableUniqueAmount признак допустимости уникализации суммы
 * @param callbackUrl адрес для колл-бэков
 * @param userId идентификатор конечного пользователя, который будет совершать оплату
 */
@JsonPropertyOrder(alphabetic = true)
public record CreateOrderRequest(
        @NotBlank(message = "should be not blank") String internalId,
        @NotNull(message = "should be not empty")
        @Positive(message = "should be positive") Integer amount,
        @NotEmpty(message = "should contains at least one") Set<RequestMethod> methods,
        Boolean enableUniqueAmount,
        @URL(protocol = "https", message = "should be valid and starts with https")
        String callbackUrl,
        String userId
) {}
