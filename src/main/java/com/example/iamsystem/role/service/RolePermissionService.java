package com.example.iamsystem.role.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.common.exception.DataNotFoundException;
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

import static com.example.iamsystem.common.exception.ErrorMessage.PERMISSIONS_NOT_FOUND;

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
        role.getPermissions().addAll(getExistingPermissions(request.permissionIds()));
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
        getExistingPermissions(request.permissionIds()).forEach(role.getPermissions()::remove);
        roleRepository.save(role);
    }

    private Role getModifiableRole(Long roleId) {
        AuthenticatedActor actor = currentUserProvider.requireCurrentActor();
        Role role = roleRepository.getRequiredByIdAndTenantId(roleId, actor.tenantId());
        accessPolicy.checkCanModify(actor, role);
        return role;
    }

    private List<Permission> getExistingPermissions(Set<Long> permissionIds) {
        List<Permission> permissions = permissionRepository.findAllById(permissionIds);
        if (permissions.size() != permissionIds.size()) {
            throw new DataNotFoundException(PERMISSIONS_NOT_FOUND);
        }
        return permissions;
    }
}
