package com.example.iamsystem.user.repository;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static com.example.iamsystem.common.exception.ErrorMessage.USER_NOT_FOUND;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    default User getRequiredById(Long id) {
        return findById(id).orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));
    }

    default User getRequiredByUsername(String username) {
        return findByUsername(username).orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));
    }

    default User getRequiredByEmail(String email) {
        return findByEmail(email).orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));
    }

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.failedLoginAttempts = :attempts WHERE u.username = :username")
    void updateFailedLoginAttempts(@Param("username") String username, @Param("attempts") int attempts);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.userLocked = :locked, u.accountLockedUntil = :lockedUntil WHERE u.username = :username")
    void updateAccountLockStatus(@Param("username") String username, @Param("locked") boolean locked, @Param("lockedUntil") Instant lockedUntil);
}
