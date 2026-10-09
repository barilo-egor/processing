package net.rcetech.domain.model.clients;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import net.rcetech.domain.model.billing.WithdrawalRequest;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.meta.orders.RequestMethod;
import org.hibernate.annotations.SortNatural;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

@Entity
@Data
@Table(name = "client")
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"orders"})
public class Client implements Persistable<UUID> {

    public static final Integer DEFAULT_ORDER_TIMEOUT = 900;

    /**
     * Идентификатор пользователя.
     */
    @Id
    private UUID id;

    @Version
    private Long version;

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
     * Адрес для отправки уведомлений о смене статусов ордера.
     */
    @Column
    private String callbackUrl;

    /**
     * Количество секунд, после которого сделки клиента считаются истекшими.
     */
    @Column(nullable = false)
    private Integer orderTimeoutSeconds = DEFAULT_ORDER_TIMEOUT;

    /**
     * Апи-ключи клиента для программной интеграции, создаются клиентом
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "client")
    @JsonIgnore
    private List<ApiKey> apiKeys;

    /**
     * Заявки на вывод средств клиента, созданные клиентом
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "client")
    @JsonIgnore
    private List<WithdrawalRequest> withdrawalRequests;

    /**
     * Ордера клиента, созданные клиентом
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "client")
    @JsonIgnore
    private List<Order> orders;

    /**
     * Процент комиссии площадки с каждого ордера
     */
    @Column
    private BigDecimal commissionPercent;

    @Column
    @PositiveOrZero(message = "should be positive or zero.")
    private Integer balance;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "client_method", joinColumns = @JoinColumn(name = "client_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "method")
    @SortNatural
    private SortedSet<RequestMethod> methods = new TreeSet<>();

    private String comment;

    private Integer minWithdrawalAmount;

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

    @PrePersist
    protected void onCreate() {
        if (balance == null) {
            balance = 0;
        }
    }
}
