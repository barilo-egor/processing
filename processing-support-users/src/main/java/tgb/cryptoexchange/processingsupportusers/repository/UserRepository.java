package tgb.cryptoexchange.processingsupportusers.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tgb.cryptoexchange.processingsupportusers.entity.SupportUser;

import java.util.Optional;

public interface UserRepository extends JpaRepository<SupportUser, Long>, JpaSpecificationExecutor<SupportUser> {

    boolean existsByUsername(String username);

    Optional<SupportUser> findByUsername(String username);

    Optional<SupportUser> findSupportUserById(Long id);

}

