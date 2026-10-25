package com.example.iamsystem.role.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.repository.PermissionRepository;
import com.example.iamsystem.role.dto.RolePermissionDto;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;


/**
 * Attaches permissions to, and detaches them from, the roles of the current user's tenant. A standard tenant's roles
 * hold TENANT permissions only; the platform tenant's roles may also hold PLATFORM ones, which take effect only when the
 * role is granted on tenants (platform grants), never through a normal role assignment.
 */
@Service
@RequiredArgsConstructor
public class RolePermissionService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleAccessPolicy accessPolicy;
    private final CurrentUserProvider currentUserProvider;

    @Auditable(
            value = AuditEventType.PERMISSIONS_ASSIGNED_TO_ROLE,
            target = "#request.roleId()",
            detailsExpression = "T(java.util.Map).of('role_id', #request.roleId(), 'permission_ids', #request.permissionIds())"
    )
    @Transactional
    public void assignPermissions(RolePermissionDto request) {
        Role role = getModifiableRole(request.roleId());
        role.getPermissions().addAll(permissionsFor(role, request.permissionIds()));
        roleRepository.save(role);
    }

    @Auditable(
            value = AuditEventType.PERMISSIONS_REMOVED_FROM_ROLE,
            target = "#request.roleId()",
            detailsExpression = "T(java.util.Map).of('role_id', #request.roleId(), 'permission_ids', #request.permissionIds())"
    )
    @Transactional
    public void removePermissions(RolePermissionDto request) {
        Role role = getModifiableRole(request.roleId());
        permissionsFor(role, request.permissionIds()).forEach(role.getPermissions()::remove);
        roleRepository.save(role);
    }

    private List<Permission> permissionsFor(Role role, Set<Long> permissionIds) {
        return role.getTenant().isPlatform()
                ? permissionRepository.getRequiredPermissions(permissionIds)
                : permissionRepository.getRequiredTenantPermissions(permissionIds);
    }

    private Role getModifiableRole(Long roleId) {
        AuthenticatedActor actor = currentUserProvider.requireCurrentActor();
        Role role = roleRepository.getRequiredByIdAndTenantId(roleId, actor.tenantId());
        accessPolicy.checkCanModify(actor, role);
        return role;
    }
}
