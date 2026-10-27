package com.example.iamsystem.otp.entity;

import com.example.iamsystem.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One request that emailed (or tried to email) a one-time code of a purpose to the user. Only counted, never changed.
 */
@Entity
@Table(name = "otp_requests")
@Getter
@NoArgsConstructor
public class OtpRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false)
    private OtpPurpose purpose;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    public OtpRequest(User user, OtpPurpose purpose, Instant requestedAt) {
        this.user = user;
        this.purpose = purpose;
        this.requestedAt = requestedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OtpRequest other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return OtpRequest.class.hashCode();
    }
}
