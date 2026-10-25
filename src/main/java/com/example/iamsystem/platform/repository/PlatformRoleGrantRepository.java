package com.example.iamsystem.platform.repository;

import com.example.iamsystem.platform.entity.PlatformRoleGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlatformRoleGrantRepository extends JpaRepository<PlatformRoleGrant, Long> {

    List<PlatformRoleGrant> findAllByUserIdOrderById(Long userId);

    /**
     * A {@code null} tenant id finds the grant on all tenants.
     */
    Optional<PlatformRoleGrant> findByUserIdAndRoleIdAndTenantId(Long userId, Long roleId, Long tenantId);

    /**
     * A {@code null} tenant id finds the grants on all tenants.
     */
    List<PlatformRoleGrant> findAllByUserIdAndRoleIdInAndTenantId(Long userId, Collection<Long> roleIds, Long tenantId);
}
