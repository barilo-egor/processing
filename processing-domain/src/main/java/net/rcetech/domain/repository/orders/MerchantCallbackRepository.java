package net.rcetech.domain.repository.orders;

import net.rcetech.domain.model.orders.MerchantCallback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MerchantCallbackRepository extends JpaRepository<MerchantCallback, Long>,
        JpaSpecificationExecutor<MerchantCallback> {
}
