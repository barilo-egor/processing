package net.rcetech.orders.config;

import lombok.Data;
import net.rcetech.meta.orders.MerchantStatusRecognizer;
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
public class MerchantStatusProperties implements MerchantStatusRecognizer {

    private List<String> successStatuses = new ArrayList<>();

    private List<String> failStatuses = new ArrayList<>();

    @Override
    public boolean isSuccess(String status) {
        if (StringUtils.isBlank(status))
            return false;
        return successStatuses.stream()
                .anyMatch(s -> s.equalsIgnoreCase(status.trim()));
    }

    @Override
    public boolean isFail(String status) {
        if (StringUtils.isBlank(status))
            return false;
        return failStatuses.stream()
                .anyMatch(s -> s.equalsIgnoreCase(status.trim()));
    }

}