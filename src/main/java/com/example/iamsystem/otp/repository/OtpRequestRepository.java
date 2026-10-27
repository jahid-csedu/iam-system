package com.example.iamsystem.otp.repository;

import com.example.iamsystem.otp.entity.OtpPurpose;
import com.example.iamsystem.otp.entity.OtpRequest;
import com.example.iamsystem.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface OtpRequestRepository extends JpaRepository<OtpRequest, Long> {

    long countByUserAndPurposeAndRequestedAtAfter(User user, OtpPurpose purpose, Instant since);

    @Modifying
    @Query("DELETE FROM OtpRequest r WHERE r.user = :user AND r.requestedAt <= :before")
    void deleteOlderThan(@Param("user") User user, @Param("before") Instant before);
}
