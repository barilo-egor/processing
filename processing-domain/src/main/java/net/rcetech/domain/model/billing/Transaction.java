package net.rcetech.domain.model.billing;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.billing.TransactionType;

import java.time.Instant;

@Entity
@Table(name = "transaction")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Идентификатор клиента в микросервисе clients.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, name = "client_id")
    private Client client;

    /**
     * Временная метка создания транзакции
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Тип операции
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Operation operation;

    /**
     * Сумма транзакции.
     */
    @Column(nullable = false)
    private Integer amount;

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

}
