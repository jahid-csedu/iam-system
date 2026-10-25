package com.example.iamsystem.user.repository;

import com.example.iamsystem.common.exception.DataNotFoundException;
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
     * Lockout bookkeeping, written directly so it never conflicts with a concurrent change of the user ({@code @Version}).
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
