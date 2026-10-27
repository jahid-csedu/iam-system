package com.example.iamsystem.emailverification.repository;

import com.example.iamsystem.emailverification.entity.EmailVerificationCode;
import com.example.iamsystem.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    /**
     * Locks the code row until the transaction ends, so parallel guesses are counted one after another and can't slip past
     * the attempt limit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerificationCode> findForUpdateByUser(User user);

    @Modifying
    @Query("DELETE FROM EmailVerificationCode c WHERE c.user = :user")
    void deleteByUser(@Param("user") User user);
}
