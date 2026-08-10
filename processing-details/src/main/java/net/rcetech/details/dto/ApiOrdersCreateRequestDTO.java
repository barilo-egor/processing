package net.rcetech.details.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ApiOrdersCreateRequestDTO {

    private UUID id;

    /**
     * Идентификатор клиента, ордер для которого был создан.
     */
    private Long clientId;

    /**
     * Идентификатор ордера в сторонней системе, получен в запросе.
     */
    private String internalId;

    private String merchant;

    private String merchantOrderId;

    private String merchantOrderStatus;

    private Integer amount;

    private boolean enableUniqueAmount;

    private String callbackUrl;

}
