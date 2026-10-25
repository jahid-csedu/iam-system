package com.example.iamsystem.session.repository;

import com.example.iamsystem.session.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Marks the token used unless it already is; 0 means another request used it first.
     */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);
}
