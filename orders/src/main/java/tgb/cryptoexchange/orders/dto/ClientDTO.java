package tgb.cryptoexchange.orders.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tgb.cryptoexchange.orders.enums.ClientStatus;

import java.time.Instant;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientDTO {

    /**
     * Идентификатор пользователя.
     */
    private Long id;

    /**
     * Уникальное имя пользователя.
     */
    private String username;

    /**
     * Префикс, первые четыре символа ключа после префикса, а также последние четыре символа контрольной суммы.
     */
    private String apiKeyPreview;

    /**
     * Дата регистрации.
     */
    private Instant registeredAt;

    /**
     * Статус пользователя.
     */
    private ClientStatus status;

    /**
     * Адрес для отправки уведомлений о смене статусов ордера.
     */
    private String callbackUrl;

    /**
     * Количество секунд, после которого сделки клиента считаются истекшими.
     */
    private Integer orderTimeoutSeconds;

}
