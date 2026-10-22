package net.rcetech.domain.repository.orders;

import net.rcetech.domain.model.orders.MerchantCallback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MerchantCallbackRepository extends JpaRepository<MerchantCallback, Long>,
        JpaSpecificationExecutor<MerchantCallback> {

    List<MerchantCallback> findAllByMerchantAndMerchantOrderIdInAndProcessedAtIsNullOrderByCreatedAtAscIdAsc(
            Merchant merchant, Collection<String> merchantOrderIds);

    @Modifying
    @Query("update MerchantCallback c set c.order = null where c.order.id in (select o.id from Order o where o.client.id = :clientId)")
    int detachOrdersByClientId(UUID clientId);
}
