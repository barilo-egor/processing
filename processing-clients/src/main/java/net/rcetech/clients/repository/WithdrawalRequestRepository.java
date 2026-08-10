package net.rcetech.clients.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import net.rcetech.clients.entity.WithdrawalRequest;

public interface WithdrawalRequestRepository
        extends JpaRepository<WithdrawalRequest, Long>, JpaSpecificationExecutor<WithdrawalRequest> {

}
