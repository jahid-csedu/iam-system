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
        return requireAll(ids, findAllByIdInAndScope(ids, PermissionScope.TENANT));
    }

    /**
     * The permissions with these ids, of any scope: what the platform tenant's roles may hold.
     * Unknown permissions count as missing.
     */
    default List<Permission> getRequiredPermissions(Set<Long> ids) {
        return requireAll(ids, findAllById(ids));
    }

    private static List<Permission> requireAll(Set<Long> ids, List<Permission> found) {
        if (found.size() != ids.size()) {
            throw new DataNotFoundException(PERMISSIONS_NOT_FOUND);
        }
        return found;
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
     * The names of every permission the user holds through a role or directly, of any scope.
     */
    @Query(nativeQuery = true, value = """
            SELECT p.name FROM user_roles ur
            JOIN role_permissions rp ON rp.role_id = ur.role_id
            JOIN permissions p ON p.id = rp.permission_id
            WHERE ur.user_id = :userId
            UNION
            SELECT p.name FROM user_permissions up
            JOIN permissions p ON p.id = up.permission_id
            WHERE up.user_id = :userId
            """)
    Set<String> findNamesGrantedToUser(@Param("userId") Long userId);

    /**
     * Whether a platform user holds a permission inside a tenant through a grant on that tenant or on all tenants: a
     * granted role that contains it, or the permission granted directly.
     */
    default boolean isGrantedToPlatformUserInTenant(Long userId, String name, Long tenantId) {
        List<Long> grantedTenantIds = findGrantedTenantIds(userId, name);
        return grantedTenantIds.stream().anyMatch(id -> id == null || id.equals(tenantId));
    }

    /**
     * The tenants a platform user's grants give a permission on, through a granted role that contains it or the
     * permission granted directly. A {@code null} entry is a grant on all tenants.
     */
    @Query(nativeQuery = true, value = """
            SELECT g.tenant_id FROM platform_role_grants g
            JOIN role_permissions rp ON rp.role_id = g.role_id
            JOIN permissions p ON p.id = rp.permission_id
            WHERE g.user_id = :userId AND p.name = :name
            UNION
            SELECT g.tenant_id FROM platform_permission_grants g
            JOIN permissions p ON p.id = g.permission_id
            WHERE g.user_id = :userId AND p.name = :name
            """)
    List<Long> findGrantedTenantIds(@Param("userId") Long userId, @Param("name") String name);

    /**
     * The names of every permission a platform user's grants give on this tenant, through grants on that tenant or on
     * all tenants: the permissions of a granted role, or a permission granted directly. Of any scope.
     */
    @Query(nativeQuery = true, value = """
            SELECT p.name FROM platform_role_grants g
            JOIN role_permissions rp ON rp.role_id = g.role_id
            JOIN permissions p ON p.id = rp.permission_id
            WHERE g.user_id = :userId AND (g.tenant_id IS NULL OR g.tenant_id = :tenantId)
            UNION
            SELECT p.name FROM platform_permission_grants g
            JOIN permissions p ON p.id = g.permission_id
            WHERE g.user_id = :userId AND (g.tenant_id IS NULL OR g.tenant_id = :tenantId)
            """)
    Set<String> findNamesGrantedToPlatformUserInTenant(@Param("userId") Long userId, @Param("tenantId") Long tenantId);
}
