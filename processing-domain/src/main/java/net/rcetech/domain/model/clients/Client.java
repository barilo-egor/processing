package net.rcetech.domain.model.clients;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.rcetech.meta.clients.ClientStatus;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@Table(name = "client")
@AllArgsConstructor
@NoArgsConstructor
public class Client implements Persistable<UUID> {

    /**
     * Идентификатор пользователя.
     */
    @Id
    private UUID id;

    /**
     * Уникальное имя пользователя.
     */
    @Column(nullable = false, unique = true)
    private String username;

    /**
     * Дата регистрации.
     */
    @Column(nullable = false, updatable = false)
    private Instant registeredAt;

    /**
     * Статус пользователя.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientStatus status;

    /**
     * Адрес для отправки уведомлений о смене статусов ордера.
     */
    @Column
    private String callbackUrl;

    /**
     * Количество секунд, после которого сделки клиента считаются истекшими.
     */
    @Column(nullable = false)
    private Integer orderTimeoutSeconds = 900;

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
