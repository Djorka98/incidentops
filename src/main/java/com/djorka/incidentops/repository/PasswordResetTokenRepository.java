package com.djorka.incidentops.repository;

import com.djorka.incidentops.model.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordResetToken token join fetch token.user where token.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            update PasswordResetToken token
            set token.usedAt = :usedAt
            where token.user.id = :userId and token.usedAt is null
            """)
    int invalidateActiveTokens(@Param("userId") Long userId, @Param("usedAt") Instant usedAt);

    @Modifying
    @Query("""
            update PasswordResetToken token
            set token.usedAt = :usedAt
            where token.user.id = :userId and token.id <> :tokenId and token.usedAt is null
            """)
    int invalidateOtherActiveTokens(
            @Param("userId") Long userId,
            @Param("tokenId") Long tokenId,
            @Param("usedAt") Instant usedAt);
}
