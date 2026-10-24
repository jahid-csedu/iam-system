package com.example.iamsystem.role.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.permission.repository.PermissionRepository;
import com.example.iamsystem.role.dto.RolePermissionDto;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Attaches permissions to, and detaches them from, the roles of the current user's tenant.
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
        role.getPermissions().addAll(permissionRepository.getRequiredTenantPermissions(request.permissionIds()));
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
        permissionRepository.getRequiredTenantPermissions(request.permissionIds()).forEach(role.getPermissions()::remove);
        roleRepository.save(role);
    }

    private Role getModifiableRole(Long roleId) {
        AuthenticatedActor actor = currentUserProvider.requireCurrentActor();
        Role role = roleRepository.getRequiredByIdAndTenantId(roleId, actor.tenantId());
        accessPolicy.checkCanModify(actor, role);
        return role;
    }
}
