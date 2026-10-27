package com.example.iamsystem.emailverification.entity;

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
 * The pending email verification code of a user (at most one), with the number of wrong guesses made against it. Only a
 * bcrypt hash of the code is stored; the code itself exists only in the email.
 */
@Entity
@Table(name = "email_verification_codes")
@Getter
@Setter
@NoArgsConstructor
public class EmailVerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmailVerificationCode other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return EmailVerificationCode.class.hashCode();
    }

    @Override
    public String toString() {
        return "EmailVerificationCode[id=" + id + ", codeHash=" + MASK + ", expiresAt=" + expiresAt
                + ", failedAttempts=" + failedAttempts + "]";
    }
}
