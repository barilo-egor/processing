package tgb.cryptoexchange.clientsapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tgb.cryptoexchange.clientsapi.entity.WithdrawalRequest;

public interface WithdrawalRequestRepository
        extends JpaRepository<WithdrawalRequest, Long>, JpaSpecificationExecutor<WithdrawalRequest> {

}
