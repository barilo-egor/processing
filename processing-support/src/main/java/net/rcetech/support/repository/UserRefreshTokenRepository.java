package net.rcetech.support.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import net.rcetech.support.entity.RefreshToken;

import java.util.UUID;

public interface UserRefreshTokenRepository
        extends JpaRepository<RefreshToken, UUID>, JpaSpecificationExecutor<RefreshToken> {

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.userId = :userId")
    void deleteByUserId(Long userId);

}
