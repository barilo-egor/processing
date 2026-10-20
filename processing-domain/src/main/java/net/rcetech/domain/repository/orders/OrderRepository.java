package net.rcetech.domain.repository.orders;

import net.rcetech.domain.model.orders.Order;
import net.rcetech.meta.orders.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByMerchantAndMerchantOrderId(Merchant merchant, String merchantOrderId);

    List<Order> findAllByStatusAndExpiresAtBefore(OrderStatus status, Instant now);

    List<Order> findAllByStatusAndCreatedAtBefore(OrderStatus status, Instant createdAt);

    boolean existsByInternalId(String internalId);

    boolean existsByIdAndClientId(UUID id, UUID clientId);

    <T> Optional<T> findByIdAndClientId(UUID id, UUID clientId, Class<T> projectionType);
}
