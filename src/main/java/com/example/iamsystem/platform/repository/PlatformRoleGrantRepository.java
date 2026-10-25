package com.example.iamsystem.platform.repository;

import com.example.iamsystem.platform.entity.PlatformRoleGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformRoleGrantRepository extends JpaRepository<PlatformRoleGrant, Long> {

    List<PlatformRoleGrant> findAllByUserIdOrderById(Long userId);

    /**
     * A {@code null} tenant id finds the grant on all tenants.
     */
    boolean existsByUserIdAndRoleIdAndTenantId(Long userId, Long roleId, Long tenantId);
}
