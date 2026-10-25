package com.example.iamsystem.session.repository;

import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.session.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    /**
     * The actor behind an access token: found only while its session is live, the user is active and unlocked, and the
     * user's tenant and the session's client are active.
     */
    @Query("""
            SELECT new com.example.iamsystem.security.userdetails.AuthenticatedActor(
                u.id, u.username, u.tenant.id, u.isRootUser, s.id)
            FROM UserSession s JOIN s.user u
            WHERE s.id = :sessionId AND u.id = :userId
              AND s.revokedAt IS NULL AND s.expiresAt > :now
              AND u.active = true AND (u.userLocked = false OR u.accountLockedUntil < :now)
              AND u.tenant.status = com.example.iamsystem.tenant.entity.TenantStatus.ACTIVE
              AND s.client.status = com.example.iamsystem.client.entity.ClientStatus.ACTIVE
            """)
    Optional<AuthenticatedActor> findActor(@Param("sessionId") Long sessionId,
                                           @Param("userId") Long userId,
                                           @Param("now") Instant now);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now WHERE s.id = :sessionId AND s.revokedAt IS NULL")
    int revoke(@Param("sessionId") Long sessionId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now WHERE s.user.id = :userId AND s.revokedAt IS NULL")
    int revokeAllOfUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE UserSession s SET s.revokedAt = :now
            WHERE s.user.id = :userId AND s.id <> :keptSessionId AND s.revokedAt IS NULL
            """)
    int revokeOtherSessionsOfUser(@Param("userId") Long userId,
                                  @Param("keptSessionId") Long keptSessionId,
                                  @Param("now") Instant now);
}
