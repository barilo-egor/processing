package net.rcetech.domain.repository.clients;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import net.rcetech.domain.model.clients.Client;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long>, JpaSpecificationExecutor<Client> {

    boolean existsByUsername(String username);

    Optional<Client> findByApiKey(String apiKey);

    Optional<Client> findByUsername(String username);

}
