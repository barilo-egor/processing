package net.rcetech.domain.repository.clients;

import jakarta.persistence.LockModeType;
import net.rcetech.domain.model.clients.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID>, JpaSpecificationExecutor<Client> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Client> findForUpdateById(UUID id);
}
