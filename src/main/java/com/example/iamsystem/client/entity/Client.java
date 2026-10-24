package com.example.iamsystem.client.entity;

import com.example.iamsystem.tenant.entity.Tenant;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * An application of a tenant that users log in through. The public {@code clientId} identifies the tenant at login,
 * and the token settings decide what the tokens issued through this client look like.
 */
@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "client_id", nullable = false, unique = true, updatable = false)
    private String clientId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ClientType type;

    @Column(name = "access_token_ttl_seconds", nullable = false)
    private int accessTokenTtlSeconds;

    @Column(name = "refresh_token_enabled", nullable = false)
    private boolean refreshTokenEnabled;

    @Column(name = "refresh_token_ttl_seconds", nullable = false)
    private int refreshTokenTtlSeconds;

    /**
     * The user claims this client's access tokens carry, on top of sub, client_id, version, iat and exp.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "client_token_claims", joinColumns = @JoinColumn(name = "client_ref_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "claim", nullable = false)
    private Set<TokenClaim> tokenClaims = new HashSet<>();

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public boolean isManagementConsole() {
        return type == ClientType.MANAGEMENT_CONSOLE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Client other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Client.class.hashCode();
    }
}
