package net.rcetech.domain.model.orders;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.rcetech.meta.orders.ClientCallbackStatus;
import net.rcetech.meta.orders.OrderStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "client_callback")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ClientCallback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false)
    private UUID orderId;

    @Column(nullable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus;

    @Column(nullable = false, updatable = false)
    private String url;

    @Column(nullable = false, updatable = false)
    private String payload;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ClientCallbackStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    private Instant lastAttemptAt;

    private Instant sentAt;
}
