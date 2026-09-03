package net.rcetech.api.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import net.rcetech.api.enums.RequestMethod;
import org.hibernate.validator.constraints.URL;

import java.util.Set;

@JsonPropertyOrder(alphabetic = true)
public record CreateOrderDTO(
        @NotBlank(message = "Обязательно для заполнения") String internalId,
        @NotNull(message = "Обязательно для заполнения")
        @Positive(message = "Сумма должна быть больше нуля") Integer amount,
        @NotEmpty(message = "Требуется хотя бы один метод") Set<RequestMethod> methods,
        Boolean enableUniqueAmount,
        @URL(protocol = "https", message = "URL должен быть валидным и с протоколом http.")
        String callbackUrl,
        String userId
) {}
