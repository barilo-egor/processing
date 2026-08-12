package net.rcetech.domain.model.clients;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.rcetech.meta.clients.WithdrawalRequestStatus;

import java.time.Instant;

@Entity
@Data
@Builder
@Table(name = "withdrawal_request")
@AllArgsConstructor
@NoArgsConstructor
public class WithdrawalRequest {

    /**
     * Идентификатор заявки. Генерируемое значение.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Идентификатор клиента {@link Client#getId()}.
     */
    @Column(name = "client_id", nullable = false)
    private Long clientId;

    /**
     * Сумма вывода.
     */
    @Column(nullable = false)
    private Integer amount;

    /**
     * Временная метка создания заявки.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Статус заявки.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WithdrawalRequestStatus status;

    /**
     * Кошелек, на который запрошен вывод.
     */
    @Column(nullable = false)
    private String wallet;

    /**
     * Комментарий к заявке.
     */
    @Column
    private String comment;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

}
