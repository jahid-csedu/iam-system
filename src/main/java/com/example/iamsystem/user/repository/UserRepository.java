package com.example.iamsystem.user.repository;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    boolean existsByTenantIdAndIsRootUserTrue(Long tenantId);

    boolean existsByIdAndRolesId(Long id, Long roleId);

    /**
     * Locks the user's row until the transaction ends, so per-user checks such as the password-reset request limit run
     * one after another. Plain reads of the user don't wait, but updates and new rows referencing the user (a login's
     * session) do, so keep such transactions short.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> lockById(@Param("id") Long id);

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
     * Lockout bookkeeping ({@code FailedPasswordCounter}), written directly so it never conflicts with a concurrent change of
     * the user ({@code @Version}). The increment is one statement, so parallel failures are all counted.
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.failedLoginAttempts = COALESCE(u.failedLoginAttempts, 0) + 1 WHERE u.id = :userId")
    void incrementFailedLoginAttempts(@Param("userId") Long userId);

    @Query("SELECT COALESCE(u.failedLoginAttempts, 0) FROM User u WHERE u.id = :userId")
    int findFailedLoginAttempts(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.userLocked = false, u.accountLockedUntil = null WHERE u.id = :userId")
    void clearFailedLogins(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.userLocked = :locked, u.accountLockedUntil = :lockedUntil WHERE u.id = :userId")
    void updateAccountLockStatus(@Param("userId") Long userId, @Param("locked") boolean locked, @Param("lockedUntil") Instant lockedUntil);
}
