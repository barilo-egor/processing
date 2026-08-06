package tgb.cryptoexchange.processingdetailsapi.dto;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import tgb.cryptoexchange.processingdetailsapi.enums.ClientStatus;

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
