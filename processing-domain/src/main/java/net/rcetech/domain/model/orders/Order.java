package net.rcetech.domain.model.orders;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.RequestMethod;
import org.springframework.data.domain.Persistable;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class Order implements Persistable<UUID> {

    @Id
    private UUID id;

    /**
     * Время создания ордера
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Время истечения ордера
     */
    private Instant expiresAt;

    /**
     * Клиент
     */
    @ManyToOne(fetch =  FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Client client;

    /**
     * Идентификатор ордера в сторонней системе.
     */
    @Column(nullable = false, unique = true)
    private String internalId;

    /**
     * Статус ордера
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    /**
     * Сумма, запрошенная клиентом.
     */
    @Column(nullable = false)
    private Integer amount;

    /**
     * Признак того, была ли разрешена уникализация клиентом.
     */
    @Builder.Default
    @Column(nullable = false)
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
    @Column(nullable = false, unique = true)
    private String merchantOrderId;

    /**
     * Статус ордера в системе мерчанта.
     */
    @Column(nullable = false)
    private String merchantOrderStatus;

    /**
     * Метод, по которому был запрошен и получен реквизит
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RequestMethod method;

    /**
     * Данные реквизита: номер карты, телефона, счета и т.п.
     */
    @Column(nullable = false)
    private String details;

    /**
     * Банк, сотовый оператор и т.п, иначе организация, которая выдала реквизит
     */
    @Column(nullable = false)
    private String bank;

    /**
     * URL на который будет отправлен HTTP запрос об изменении статуса с информацией об ордере.
     */
    @Column(nullable = false)
    private String callbackUrl;

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
