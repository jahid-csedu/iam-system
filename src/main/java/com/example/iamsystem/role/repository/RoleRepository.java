package com.example.iamsystem.role.repository;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.role.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.example.iamsystem.common.exception.ErrorMessage.ROLE_NOT_FOUND;

/**
 * Every lookup is tenant-scoped; a role of another tenant is simply not found.
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    List<Role> findAllByTenantIdOrderByName(Long tenantId);

    List<Role> findAllByIdInAndTenantId(Collection<Long> ids, Long tenantId);

    Optional<Role> findByIdAndTenantId(Long id, Long tenantId);

    Optional<Role> findByNameAndTenantId(String name, Long tenantId);

    boolean existsByNameAndTenantId(String name, Long tenantId);

    boolean existsByNameAndTenantIdAndIdNot(String name, Long tenantId, Long id);

    default Role getRequiredByIdAndTenantId(Long id, Long tenantId) {
        return findByIdAndTenantId(id, tenantId).orElseThrow(() -> new DataNotFoundException(ROLE_NOT_FOUND));
    }

    default Role getRequiredByNameAndTenantId(String name, Long tenantId) {
        return findByNameAndTenantId(name, tenantId).orElseThrow(() -> new DataNotFoundException(ROLE_NOT_FOUND));
    }
}
