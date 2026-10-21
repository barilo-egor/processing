package net.rcetech.domain.repository.orders;

import net.rcetech.domain.model.orders.MerchantCallback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Collection;
import java.util.List;

public interface MerchantCallbackRepository extends JpaRepository<MerchantCallback, Long>,
        JpaSpecificationExecutor<MerchantCallback> {

    List<MerchantCallback> findAllByMerchantAndMerchantOrderIdInAndProcessedAtIsNullOrderByCreatedAtAscIdAsc(
            Merchant merchant, Collection<String> merchantOrderIds);
}
