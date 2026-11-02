package net.rcetech.api.merchantdetails;

import lombok.experimental.UtilityClass;
import net.rcetech.meta.orders.RequestMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@UtilityClass
public final class DetailsValidator {

    /**
     * Проверяет, что реквизиты от merchant-details можно выдать клиенту.
     *
     * @param response         ответ merchant-details
     * @param requestedMethods методы оплаты из запроса клиента
     * @return причины отклонения через запятую или пустой Optional, если реквизиты корректны
     */
    public static Optional<String> validate(ApiDetailsResponse response, Set<RequestMethod> requestedMethods) {
        List<String> violations = new ArrayList<>();
        if (Objects.isNull(response.merchant())) {
            violations.add("не указан мерчант");
        }
        if (isBlank(response.orderId())) {
            violations.add("не указан идентификатор ордера мерчанта");
        }
        if (Objects.nonNull(response.amount()) && response.amount() <= 0) {
            violations.add("некорректная сумма " + response.amount());
        }
        ApiDetailsResponse.Details details = response.details();
        if (Objects.isNull(details)) {
            violations.add("нет реквизитов");
            return Optional.of(String.join(", ", violations));
        }
        if (Objects.isNull(details.requestMethod()) || !requestedMethods.contains(details.requestMethod())) {
            violations.add("метод оплаты " + details.requestMethod() + " не входит в запрошенные");
        }
        if (isBlank(details.details())) {
            violations.add("пустые реквизиты");
        } else if (isLink(details.details())) {
            violations.add("реквизиты переданы ссылкой");
        }
        if (isBlank(details.bank())) {
            violations.add("не указан банк");
        }
        return violations.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", violations));
    }

    private static boolean isBlank(String value) {
        return Objects.isNull(value) || value.isBlank();
    }

    private static boolean isLink(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }
}
