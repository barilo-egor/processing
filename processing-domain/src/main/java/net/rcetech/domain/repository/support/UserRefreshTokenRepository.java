package net.rcetech.domain.repository.support;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import net.rcetech.domain.model.support.RefreshToken;

import java.util.UUID;

public interface UserRefreshTokenRepository
        extends JpaRepository<RefreshToken, UUID>, JpaSpecificationExecutor<RefreshToken> {

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.userId = :userId")
    void deleteByUserId(Long userId);

}
