package net.rcetech.domain.repository.billing;

import jakarta.persistence.LockModeType;
import net.rcetech.domain.model.billing.WithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID>,
        JpaSpecificationExecutor<WithdrawalRequest> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WithdrawalRequest> findForUpdateById(UUID id);

    @Query("""
            select coalesce(sum(w.grossSourceAmount), 0)
            from WithdrawalRequest w
            where w.client.id = :clientId and w.status = net.rcetech.meta.billing.WithdrawalRequestStatus.NEW
            """)
    Integer calculateReservedAmount(UUID clientId);

    @Modifying
    @Query("delete from WithdrawalRequest w where w.client.id = :clientId")
    int deleteAllByClientId(UUID clientId);
}
