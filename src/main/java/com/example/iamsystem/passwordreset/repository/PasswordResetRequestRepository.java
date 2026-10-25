package com.example.iamsystem.passwordreset.repository;

import com.example.iamsystem.passwordreset.entity.PasswordResetRequest;
import com.example.iamsystem.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, Long> {

    long countByUserAndRequestedAtAfter(User user, Instant since);

    @Modifying
    @Query("DELETE FROM PasswordResetRequest r WHERE r.user = :user AND r.requestedAt <= :before")
    void deleteOlderThan(@Param("user") User user, @Param("before") Instant before);
}
