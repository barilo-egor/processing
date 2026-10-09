package net.rcetech.domain.repository.orders;

import net.rcetech.domain.model.orders.Order;
import net.rcetech.meta.orders.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByMerchantOrderId(String merchantOrderId);

    List<Order> findAllByStatusAndExpiresAtBefore(OrderStatus status, Instant now);

    boolean existsByInternalId(String internalId);
}
