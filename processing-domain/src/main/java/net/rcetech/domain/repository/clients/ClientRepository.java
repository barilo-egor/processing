package net.rcetech.domain.repository.clients;

import net.rcetech.domain.model.clients.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID>, JpaSpecificationExecutor<Client> {

    boolean existsByUsername(String username);

    Optional<Client> findByUsername(String username);
}
