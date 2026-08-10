package net.rcetech.clientsapi.dto;

import lombok.*;
import net.rcetech.clientsapi.entity.Client;
import net.rcetech.clientsapi.enums.ClientStatus;

import java.time.Instant;

/**
 * @see Client
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
