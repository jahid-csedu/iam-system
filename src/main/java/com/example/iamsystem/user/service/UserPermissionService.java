package com.example.iamsystem.user.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.permission.repository.PermissionRepository;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.user.dto.UserPermissionAttachmentDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grants permissions to, and takes them from, management users of the current user's tenant directly.
 */
@Service
@RequiredArgsConstructor
public class UserPermissionService {

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final UserAccessPolicy accessPolicy;
    private final CurrentUserProvider currentUserProvider;

    @Auditable(
            value = AuditEventType.PERMISSIONS_ASSIGNED_TO_USER,
            target = "#request.username()",
            detailsExpression = "T(java.util.Map).of('permission_ids', #request.permissionIds())"
    )
    @Transactional
    public void assignPermissions(UserPermissionAttachmentDto request) {
        User user = getManageableUser(request.username());
        accessPolicy.checkCanReceiveAccess(user);
        user.getPermissions().addAll(permissionRepository.getRequiredTenantPermissions(request.permissionIds()));
        userRepository.save(user);
    }

    @Auditable(
            value = AuditEventType.PERMISSIONS_REMOVED_FROM_USER,
            target = "#request.username()",
            detailsExpression = "T(java.util.Map).of('permission_ids', #request.permissionIds())"
    )
    @Transactional
    public void removePermissions(UserPermissionAttachmentDto request) {
        User user = getManageableUser(request.username());
        permissionRepository.getRequiredTenantPermissions(request.permissionIds()).forEach(user.getPermissions()::remove);
        userRepository.save(user);
    }

    private User getManageableUser(String username) {
        AuthenticatedActor actor = currentUserProvider.requireCurrentActor();
        User user = userRepository.getRequiredByUsernameAndTenantId(username, actor.tenantId());
        accessPolicy.checkCanManageAccess(actor, user);
        return user;
    }
}
