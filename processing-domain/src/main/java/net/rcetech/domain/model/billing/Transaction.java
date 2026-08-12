package net.rcetech.domain.model.billing;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.billing.TransactionType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class Transaction {

    @Id
    private UUID id;

    /**
     * Идентификатор клиента в микросервисе clients.
     */
    @Column(nullable = false)
    private Long clientId;

    /**
     * Сумма транзакции.
     */
    @Column(nullable = false)
    private Integer amount;

    /**
     * Тип операции
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Operation operation;

    /**
     * Тип транзакции
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType type;

    /**
     * Комментарий
     */
    @Column
    private String comment;

    /**
     * Временная метка создания транзакции
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

}
