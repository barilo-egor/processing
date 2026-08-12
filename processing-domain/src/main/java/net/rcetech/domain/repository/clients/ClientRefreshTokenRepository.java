package net.rcetech.domain.repository.clients;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import net.rcetech.domain.model.clients.ClientRefreshToken;

import java.util.UUID;

public interface ClientRefreshTokenRepository
        extends JpaRepository<ClientRefreshToken, UUID>, JpaSpecificationExecutor<ClientRefreshToken> {

    @Modifying
    @Query("DELETE FROM ClientRefreshToken t WHERE t.clientId = :clientId")
    void deleteByClientId(Long clientId);

}
