package net.rcetech.domain.model.clients;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.rcetech.meta.clients.ClientStatus;

import java.time.Instant;

@Entity
@Data
@Builder
@Table(name = "client")
@AllArgsConstructor
@NoArgsConstructor
public class Client {

    /**
     * Идентификатор пользователя.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Уникальное имя пользователя.
     */
    @Column(nullable = false, unique = true)
    private String username;

    /**
     * Хэш пароля.
     */
    @Column(nullable = false)
    private String password;

    /**
     * SHA-256 хэш апи ключа.
     */
    @Column(name = "api_key", nullable = false)
    private String apiKey;

    /**
     * Префикс, первые четыре символа ключа после префикса, а также последние четыре символа контрольной суммы.
     */
    @Column(name = "api_key_preview", nullable = false)
    private String apiKeyPreview;

    /**
     * Секретный ключ для проверки и генерации подписи.
     */
    @Column(nullable = false)
    private String secret;

    /**
     * Дата регистрации.
     */
    @Column(name = "registered_at", nullable = false, updatable = false)
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
    @Column(name = "callback_url")
    private String callbackUrl;

    /**
     * Количество секунд, после которого сделки клиента считаются истекшими.
     */
    @Builder.Default
    @Column(name = "order_timeout_seconds", nullable = false)
    private Integer orderTimeoutSeconds = 900;

    @PrePersist
    protected void onCreate() {
        if (registeredAt == null) {
            registeredAt = Instant.now();
        }
    }

}
