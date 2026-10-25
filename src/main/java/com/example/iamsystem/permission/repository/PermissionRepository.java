package com.example.iamsystem.permission.repository;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.entity.PermissionScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.example.iamsystem.common.exception.ErrorMessage.PERMISSIONS_NOT_FOUND;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByName();

    List<Permission> findAllByIdInAndScope(Collection<Long> ids, PermissionScope scope);

    /**
     * The TENANT permissions with these ids: the only ones that may be given to a tenant's roles and users.
     * Unknown or PLATFORM permissions count as missing.
     */
    default List<Permission> getRequiredTenantPermissions(Set<Long> ids) {
        List<Permission> permissions = findAllByIdInAndScope(ids, PermissionScope.TENANT);
        if (permissions.size() != ids.size()) {
            throw new DataNotFoundException(PERMISSIONS_NOT_FOUND);
        }
        return permissions;
    }

    /**
     * Whether the user holds the permission named {@code name}, through a role or directly.
     */
    @Query("""
            SELECT COUNT(u) > 0
            FROM User u
            WHERE u.id = :userId
              AND (EXISTS (SELECT 1 FROM User ru JOIN ru.roles r JOIN r.permissions rp WHERE ru = u AND rp.name = :name)
                   OR EXISTS (SELECT 1 FROM User du JOIN du.permissions dp WHERE du = u AND dp.name = :name))
            """)
    boolean isGrantedToUser(@Param("userId") Long userId, @Param("name") String name);

    /**
     * Whether a platform user holds a permission inside a tenant through a grant on that tenant or on all tenants: a
     * granted role that contains it, or the permission granted directly.
     */
    @Query(nativeQuery = true, value = """
            SELECT EXISTS (
                SELECT 1 FROM platform_role_grants g
                JOIN role_permissions rp ON rp.role_id = g.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE g.user_id = :userId AND p.name = :name AND (g.tenant_id = :tenantId OR g.tenant_id IS NULL)
                UNION ALL
                SELECT 1 FROM platform_permission_grants g
                JOIN permissions p ON p.id = g.permission_id
                WHERE g.user_id = :userId AND p.name = :name AND (g.tenant_id = :tenantId OR g.tenant_id IS NULL)
            )
            """)
    boolean isGrantedToPlatformUserInTenant(@Param("userId") Long userId,
                                            @Param("name") String name,
                                            @Param("tenantId") Long tenantId);
}
