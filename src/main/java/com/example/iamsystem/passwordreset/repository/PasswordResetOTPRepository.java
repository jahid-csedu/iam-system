package com.example.iamsystem.passwordreset.repository;

import com.example.iamsystem.passwordreset.entity.PasswordResetOTP;
import com.example.iamsystem.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordResetOTPRepository extends JpaRepository<PasswordResetOTP, Long> {

    PasswordResetOTP findByOtp(String otp);
    PasswordResetOTP findByUser(User user);
}
