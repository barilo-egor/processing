package net.rcetech.domain.model.support;

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
@Table(name = "user_refresh_tokens")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class RefreshToken {

    /**
     * Сгенерированный UUID v7.
     */
    @Id
    private UUID token;

    /**
     * Идентификатор пользователя {@link SupportUser#getId()}.
     */
    @Column(nullable = false, unique = true)
    private Long userId;

    /**
     * Временная метка, обозначающая срок действия токена.
     */
    @Column(nullable = false)
    private Instant expiresAt;

}
