package tgb.cryptoexchange.clientsapi.dto;

import lombok.*;
import tgb.cryptoexchange.clientsapi.enums.ClientStatus;

import java.time.Instant;

/**
 * @see tgb.cryptoexchange.clientsapi.entity.Client
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientDTO {

    private Long id;

    private String username;

    @ToString.Exclude
    private String password;

    @ToString.Exclude
    private String apiKey;

    private String apiKeyPreview;

    @ToString.Exclude
    private String secret;

    private Instant registeredAt;

    private ClientStatus status;

    private String callbackUrl;

    private Integer orderTimeoutSeconds;

}
