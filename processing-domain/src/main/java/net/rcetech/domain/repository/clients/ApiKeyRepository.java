package net.rcetech.domain.repository.clients;

import net.rcetech.domain.model.clients.ApiKey;
import net.rcetech.meta.clients.dto.ApiKeyResponseDTO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    boolean existsByIdAndClientId(Long id, UUID clientId);

    boolean existsByClientIdAndName(UUID clientId, String name);

    List<ApiKeyResponseDTO> findAllByClientId(UUID clientId);
}
