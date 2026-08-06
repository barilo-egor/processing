package tgb.cryptoexchange.orders.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import tgb.cryptoexchange.orders.entity.Order;
import tgb.cryptoexchange.orders.enums.OrderStatus;

import java.util.Optional;
import java.util.UUID;

@Transactional
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    boolean existsByInternalId(String internalId);

    @Modifying
    @Query("UPDATE Order o SET o.status = :status WHERE o.id = :id")
    int updateStatusById(@Param("id") UUID id, @Param("status") OrderStatus status);

    @Modifying
    @Query("UPDATE Order o SET o.status = :status WHERE o.id = :id AND o.clientId = :clientId")
    int updateStatusByIdAndClientId(@Param("id") UUID id, @Param("clientId") Long clientId,
            @Param("status") OrderStatus status);

    @Modifying
    @Query("UPDATE Order o SET o.status = :status WHERE o.internalId = :internalId")
    int updateStatusByInternalId(@Param("internalId") String internalId, @Param("status") OrderStatus status);

    @Modifying
    @Query("UPDATE Order o SET o.status = :status WHERE o.internalId = :internalId AND o.clientId = :clientId")
    int updateStatusByInternalIdAndClientId(@Param("internalId") String internalId, @Param("clientId") Long clientId,
            @Param("status") OrderStatus status);

    @Modifying
    @Query("UPDATE Order o SET o.merchantOrderStatus = :merchantOrderStatus WHERE o.id = :id")
    int updateMerchantOrderStatusById(@Param("id") UUID id, @Param("merchantOrderStatus") String merchantOrderStatus);

    Order getOrdersById(UUID id);

    Optional<Order> findByMerchantOrderId(String merchantOrderId);

    Order getOrdersByInternalId(String internalId);

}
