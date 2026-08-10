package net.rcetech.orders.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.rcetech.commons.enums.Merchant;
import net.rcetech.orders.enums.OrderStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class Order {

    @Id
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Идентификатор клиента в api-clients.
     */
    @Column(nullable = false)
    private Long clientId;

    /**
     * Идентификатор ордера в сторонней системе.
     */
    @Column(nullable = false, unique = true)
    private String internalId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    /**
     * Сумма, запрошенная клиентом.
     */
    @Column(nullable = false)
    private Integer amount;

    /**
     * Индикатор того, была ли разрешена уникализация клиентом.
     */
    @Builder.Default
    @Column(nullable = false, name = "enable_unique_amount")
    private Boolean enableUniqueAmount = false;

    /**
     * Константа мерчанта, от которого были получены реквизиты.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Merchant merchant;

    /**
     * Идентификатор ордера в системе мерчанта.
     */
    @Column(nullable = false, name = "merchant_order_id")
    private String merchantOrderId;

    /**
     * Статус ордера в системе мерчанта.
     */
    @Column(nullable = false, name = "merchant_order_status")
    private String merchantOrderStatus;

    /**
     * URL на который будет отправлен HTTP запрос об изменении статуса с информацией об ордере.
     */
    @Column(name = "callback_url")
    private String callbackUrl;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

}
