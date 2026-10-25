package com.example.iamsystem.passwordreset.entity;

import com.example.iamsystem.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

import static com.example.iamsystem.common.util.SecretMask.MASK;

/**
 * The pending password-reset OTP of a user (at most one), with the number of wrong guesses made against it.
 */
@Entity
@Table(name = "password_reset_otps")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetOTP {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String otp;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant expiryDate;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    public boolean isExpired() {
        return expiryDate.isBefore(Instant.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PasswordResetOTP other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return PasswordResetOTP.class.hashCode();
    }

    @Override
    public String toString() {
        return "PasswordResetOTP[id=" + id + ", otp=" + MASK + ", expiryDate=" + expiryDate
                + ", failedAttempts=" + failedAttempts + "]";
    }
}
