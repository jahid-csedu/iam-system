package com.example.iamsystem.user.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.repository.RoleRepository;
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
        User user = getManageableUser(request.getUsername());
        user.getRoles().addAll(getExistingRoles(request.getRoleIds()));
        userRepository.save(user);
    }

    @Auditable(
            value = AuditEventType.ROLES_REMOVAL,
            target = "#request.username",
            detailsExpression = "T(java.util.Map).of('role_ids', #request.roleIds)"
    )
    @Transactional
    public void removeRoles(UserRoleAttachmentDto request) {
        User user = getManageableUser(request.getUsername());
        getExistingRoles(request.getRoleIds()).forEach(user.getRoles()::remove);
        userRepository.save(user);
    }

    private User getManageableUser(String username) {
        User actor = currentUserProvider.requireCurrentUser();
        User user = userRepository.getRequiredByUsername(username);
        accessPolicy.checkCanManageRoles(actor, user);
        return user;
    }

    private List<Role> getExistingRoles(Set<Long> roleIds) {
        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new DataNotFoundException(ROLES_NOT_FOUND);
        }
        return roles;
    }
}
