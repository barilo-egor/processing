package net.rcetech.domain.repository.billing;

import net.rcetech.domain.model.billing.WithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID>,
        JpaSpecificationExecutor<WithdrawalRequest> {
}
