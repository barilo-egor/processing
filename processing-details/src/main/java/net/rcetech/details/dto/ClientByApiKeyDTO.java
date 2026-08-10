package net.rcetech.details.dto;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import net.rcetech.details.enums.ClientStatus;

import java.time.Instant;

/**
 * Данные клиента, идентифицированного по API-ключу.
 */
@Data
@Builder
public class ClientByApiKeyDTO {

    private Long clientId;

    private String username;

    private String apiKeyPreview;

    @ToString.Exclude
    private String secret;

    private Instant registeredAt;

    private ClientStatus status;

    private String callbackUrl;

    private Integer orderTimeoutSeconds;

}
