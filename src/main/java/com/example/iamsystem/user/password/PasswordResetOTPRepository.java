package com.example.iamsystem.user.password;

import com.example.iamsystem.user.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface PasswordResetOTPRepository extends JpaRepository<PasswordResetOTP, Long> {

    PasswordResetOTP findByOtp(String otp);
    PasswordResetOTP findByUser(User user);
}
