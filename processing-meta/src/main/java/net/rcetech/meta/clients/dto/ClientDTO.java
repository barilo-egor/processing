package net.rcetech.meta.clients.dto;

import lombok.*;
import net.rcetech.meta.clients.ClientStatus;

import java.time.Instant;

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
