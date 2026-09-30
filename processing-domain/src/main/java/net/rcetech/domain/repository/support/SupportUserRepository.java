package net.rcetech.domain.repository.support;

import net.rcetech.domain.model.support.SupportUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SupportUserRepository extends JpaRepository<SupportUser, UUID>, JpaSpecificationExecutor<SupportUser> {

}

