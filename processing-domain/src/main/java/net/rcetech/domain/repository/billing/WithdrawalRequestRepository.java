package net.rcetech.domain.repository.billing;

import net.rcetech.domain.model.billing.WithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID>,
        JpaSpecificationExecutor<WithdrawalRequest> {

    @Modifying
    @Query("delete from WithdrawalRequest w where w.client.id = :clientId")
    int deleteAllByClientId(UUID clientId);
}
