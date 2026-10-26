package net.rcetech.domain.repository.clients;

import net.rcetech.domain.model.clients.IpAddress;
import net.rcetech.meta.clients.dto.IpAddressResponseDTO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IpAddressRepository extends JpaRepository<IpAddress, Long> {

    boolean existsByIdAndClientId(Long id, UUID clientId);

    boolean existsByClientIdAndAddress(UUID clientId, String address);

    Optional<IpAddress> findByIdAndClientId(Long id, UUID clientId);

    boolean existsByAddress(String address);

    long countByClientId(UUID clientId);

    List<IpAddressResponseDTO> findAllByClientIdOrderById(UUID clientId);
}
