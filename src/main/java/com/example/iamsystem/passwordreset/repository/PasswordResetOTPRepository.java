package com.example.iamsystem.passwordreset.repository;

import com.example.iamsystem.passwordreset.entity.PasswordResetOTP;
import com.example.iamsystem.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetOTPRepository extends JpaRepository<PasswordResetOTP, Long> {

    Optional<PasswordResetOTP> findByUser(User user);

    /**
     * Locks the OTP row until the transaction ends, so parallel guesses are counted one after another and can't slip past
     * the attempt limit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetOTP> findForUpdateByUser(User user);

    @Modifying
    @Query("DELETE FROM PasswordResetOTP o WHERE o.user = :user")
    void deleteByUser(User user);
}
