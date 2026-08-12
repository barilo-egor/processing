package net.rcetech.domain.repository.clients;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import net.rcetech.domain.model.clients.WithdrawalRequest;

public interface WithdrawalRequestRepository
        extends JpaRepository<WithdrawalRequest, Long>, JpaSpecificationExecutor<WithdrawalRequest> {

}
