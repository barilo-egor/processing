package net.rcetech.domain.model.clients;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "client_refresh_token")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ClientRefreshToken {

    /**
     * Сгенерированный UUID v7.
     */
    @Id
    private UUID token;

    /**
     * Идентификатор клиента {@link Client#getId()}.
     */
    @Column(nullable = false, unique = true)
    private Long clientId;

    /**
     * Временная метка, обозначающая срок действия токена.
     */
    @Column(nullable = false)
    private Instant expiresAt;

}
