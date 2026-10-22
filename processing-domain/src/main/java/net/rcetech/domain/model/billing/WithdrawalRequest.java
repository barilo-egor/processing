package net.rcetech.domain.model.billing;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.meta.billing.WithdrawalRequestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "withdrawal_request")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class WithdrawalRequest {

    @Id
    private UUID id;

    /**
     * Автор заявки
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, name = "client_id")
    private Client client;

    /**
     * Статус заявки
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    private WithdrawalRequestStatus status;

    /**
     * Временная метка создания транзакции
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Сумма заявки
     */
    @Column(nullable = false)
    @Positive
    private Integer grossSourceAmount;

    /**
     * Процент комиссии на момент создания заявки
     */
    @Column(nullable = false)
    @Positive
    private BigDecimal commissionPercent;

    /**
     * Итоговая, после применения комиссии, сумма
     */
    @Column(nullable = false)
    @Positive
    private Integer netSourceAmount;

    /**
     * Курс обмена (стоимость единицы целевой валюты в исходной)
     */
    @Column(nullable = false)
    @Positive
    private BigDecimal rate;

    /**
     * Итоговая, после применения комиссии, сумма вывода в usdt
     */
    @Column(nullable = false)
    @Positive
    private Integer targetAmount;

    /**
     * Адрес кошелька для вывода
     */
    @Column(nullable = false)
    @NotBlank
    private String address;
}
