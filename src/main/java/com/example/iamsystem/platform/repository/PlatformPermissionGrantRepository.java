package com.example.iamsystem.platform.repository;

import com.example.iamsystem.platform.entity.PlatformPermissionGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlatformPermissionGrantRepository extends JpaRepository<PlatformPermissionGrant, Long> {

    List<PlatformPermissionGrant> findAllByUserIdOrderById(Long userId);

    /**
     * A {@code null} tenant id finds the grant on all tenants.
     */
    Optional<PlatformPermissionGrant> findByUserIdAndPermissionIdAndTenantId(Long userId, Long permissionId, Long tenantId);

    /**
     * A {@code null} tenant id finds the grants on all tenants.
     */
    List<PlatformPermissionGrant> findAllByUserIdAndPermissionIdInAndTenantId(Long userId, Collection<Long> permissionIds, Long tenantId);
}
