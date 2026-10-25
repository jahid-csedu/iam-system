package com.example.iamsystem.platform.entity;

import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;

/**
 * A permission granted directly to a platform user on one tenant, or on all standard tenants when {@code tenant} is null.
 * Inside that tenant (with {@code X-Tenant-Id}, or on the platform endpoints) the user holds it.
 */
@Entity
@Table(name = "platform_permission_grants")
@Getter
@Setter
@NoArgsConstructor
public class PlatformPermissionGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id", nullable = false, updatable = false)
    private Permission permission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", updatable = false)
    private Tenant tenant;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlatformPermissionGrant other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return PlatformPermissionGrant.class.hashCode();
    }
}
