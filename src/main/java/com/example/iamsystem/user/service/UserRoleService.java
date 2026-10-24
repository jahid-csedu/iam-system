package com.example.iamsystem.user.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.user.dto.UserRoleAttachmentDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static com.example.iamsystem.common.exception.ErrorMessage.ROLES_NOT_FOUND;

/**
 * Assigns and removes roles of users in the current user's tenant.
 */
@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserAccessPolicy accessPolicy;
    private final CurrentUserProvider currentUserProvider;

    @Auditable(
            value = AuditEventType.ROLES_ASSIGNMENT,
            target = "#request.username",
            detailsExpression = "T(java.util.Map).of('role_ids', #request.roleIds)"
    )
    @Transactional
    public void assignRoles(UserRoleAttachmentDto request) {
        AuthenticatedActor actor = currentUserProvider.requireCurrentActor();
        User user = getManageableUser(actor, request.getUsername());
        user.getRoles().addAll(getTenantRoles(request.getRoleIds(), actor.tenantId()));
        userRepository.save(user);
    }

    @Auditable(
            value = AuditEventType.ROLES_REMOVAL,
            target = "#request.username",
            detailsExpression = "T(java.util.Map).of('role_ids', #request.roleIds)"
    )
    @Transactional
    public void removeRoles(UserRoleAttachmentDto request) {
        AuthenticatedActor actor = currentUserProvider.requireCurrentActor();
        User user = getManageableUser(actor, request.getUsername());
        getTenantRoles(request.getRoleIds(), actor.tenantId()).forEach(user.getRoles()::remove);
        userRepository.save(user);
    }

    private User getManageableUser(AuthenticatedActor actor, String username) {
        User user = userRepository.getRequiredByUsernameAndTenantId(username, actor.tenantId());
        accessPolicy.checkCanManageRoles(actor, user);
        return user;
    }

    /**
     * Roles of another tenant count as missing.
     */
    private List<Role> getTenantRoles(Set<Long> roleIds, Long tenantId) {
        List<Role> roles = roleRepository.findAllByIdInAndTenantId(roleIds, tenantId);
        if (roles.size() != roleIds.size()) {
            throw new DataNotFoundException(ROLES_NOT_FOUND);
        }
        return roles;
    }
}
