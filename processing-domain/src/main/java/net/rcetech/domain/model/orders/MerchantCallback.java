package net.rcetech.domain.model.orders;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;

@Entity
@Table(name = "merchant_callback")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class MerchantCallback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Merchant merchant;

    private String merchantOrderId;

    @Column(nullable = false)
    private String status;

    private String statusDescription;
}
