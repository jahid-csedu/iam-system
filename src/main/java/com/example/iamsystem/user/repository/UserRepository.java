package com.example.iamsystem.user.repository;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static com.example.iamsystem.common.exception.ErrorMessage.USER_NOT_FOUND;

/**
 * Users are always looked up inside one tenant (usernames and emails are unique per tenant); a user of another tenant
 * is simply not found. The cross-tenant {@code exists...} checks guard the global uniqueness of root users.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Page<User> findAllByTenantId(Long tenantId, Pageable pageable);

    Optional<User> findByIdAndTenantId(Long id, Long tenantId);

    Optional<User> findByUsernameAndTenantId(String username, Long tenantId);

    Optional<User> findByEmailAndTenantId(String email, Long tenantId);

    boolean existsByUsernameAndTenantId(String username, Long tenantId);

    boolean existsByEmailAndTenantId(String email, Long tenantId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsernameAndIsRootUserTrue(String username);

    boolean existsByEmailAndIsRootUserTrue(String email);

    /**
     * The actor behind an access token: found only while the token's version is current and the user is active and unlocked.
     */
    @Query("""
            SELECT new com.example.iamsystem.security.userdetails.AuthenticatedActor(u.id, u.username, u.tenant.id, u.isRootUser)
            FROM User u
            WHERE u.id = :userId AND u.version = :version AND u.active = true
              AND (u.userLocked = false OR u.accountLockedUntil < :now)
            """)
    Optional<AuthenticatedActor> findActor(@Param("userId") Long userId,
                                           @Param("version") int version,
                                           @Param("now") Instant now);

    boolean existsByIdAndRolesId(Long id, Long roleId);

    default User getRequiredByIdAndTenantId(Long id, Long tenantId) {
        return findByIdAndTenantId(id, tenantId).orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));
    }

    default User getRequiredByUsernameAndTenantId(String username, Long tenantId) {
        return findByUsernameAndTenantId(username, tenantId).orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));
    }

    default User getRequiredByEmailAndTenantId(String email, Long tenantId) {
        return findByEmailAndTenantId(email, tenantId).orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));
    }

    /**
     * Lockout bookkeeping; deliberately skips the {@code @Version} bump, so it doesn't revoke the user's tokens.
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.failedLoginAttempts = :attempts WHERE u.id = :userId")
    void updateFailedLoginAttempts(@Param("userId") Long userId, @Param("attempts") int attempts);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.userLocked = :locked, u.accountLockedUntil = :lockedUntil WHERE u.id = :userId")
    void updateAccountLockStatus(@Param("userId") Long userId, @Param("locked") boolean locked, @Param("lockedUntil") Instant lockedUntil);
}
