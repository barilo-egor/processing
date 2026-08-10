package net.rcetech.orders.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Конфигурационные свойства для обработки callback-статусов мерчантов.
 * Мапит настройки из файла конфигурации приложения.
 */
@Component
@ConfigurationProperties(prefix = "merchant.callback")
@Data
public class MerchantStatusProperties {

    private List<String> successStatuses = new ArrayList<>();

    private List<String> failStatuses = new ArrayList<>();

    /**
     * Проверяет, является ли статус успешным.
     */
    public boolean isSuccess(String status) {
        if (StringUtils.isBlank(status))
            return false;
        return successStatuses.stream()
                .anyMatch(s -> s.equalsIgnoreCase(status.trim()));
    }

    /**
     * Проверяет, является ли статус неуспешным.
     */
    public boolean isFail(String status) {
        if (StringUtils.isBlank(status))
            return false;
        return failStatuses.stream()
                .anyMatch(s -> s.equalsIgnoreCase(status.trim()));
    }

}