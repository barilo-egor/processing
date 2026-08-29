package net.rcetech.domain.repository.support;

import net.rcetech.domain.model.support.SupportUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SupportUserRepository extends JpaRepository<SupportUser, Long>, JpaSpecificationExecutor<SupportUser> {

    boolean existsByUsername(String username);

    Optional<SupportUser> findByUsername(String username);

    Optional<SupportUser> findSupportUserById(Long id);

}

