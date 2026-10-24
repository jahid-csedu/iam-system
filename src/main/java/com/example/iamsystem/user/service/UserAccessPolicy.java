package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.permission.entity.PermissionAction;
import com.example.iamsystem.permission.service.PermissionService;
import com.example.iamsystem.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.example.iamsystem.common.exception.ErrorMessage.NO_PERMISSION;
import static com.example.iamsystem.common.exception.ErrorMessage.USER_NOT_FOUND;
import static com.example.iamsystem.permission.PermissionConstants.IAM_SERVICE_NAME;
import static com.example.iamsystem.permission.entity.PermissionAction.UPDATE;
import static com.example.iamsystem.permission.entity.PermissionAction.WRITE;

/**
 * Decides which users an actor may see and manage.
 * <p>
 * Today the boundary is the creator hierarchy: an actor may act on users they created, directly or indirectly.
 * Tenant isolation will replace this class's rules.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserAccessPolicy {

    private final PermissionService permissionService;

    public boolean canView(User actor, User target) {
        return isInHierarchyOf(actor, target);
    }

    /**
     * Users outside the actor's hierarchy are reported as not found, so their existence is not revealed.
     */
    public void checkCanView(User actor, User target) {
        if (!canView(actor, target)) {
            throw new DataNotFoundException(USER_NOT_FOUND);
        }
    }

    public void checkCanCreateUsers(User actor) {
        requirePermission(actor, WRITE);
    }

    public void checkCanManageRoles(User actor, User target) {
        if (!isInHierarchyOf(actor, target)) {
            deny(actor, "manage roles of", target);
        }
        requirePermission(actor, UPDATE);
    }

    /**
     * A user may change their own password; a root user may also change the passwords of their subordinates.
     */
    public void checkCanChangePassword(User actor, User target) {
        boolean self = actor.equals(target);
        boolean rootOverSubordinate = actor.isRootUser() && isInHierarchyOf(actor, target);
        if (!self && !rootOverSubordinate) {
            deny(actor, "change the password of", target);
        }
    }

    /**
     * A root user can only be deleted by themselves; any other user by someone above them in the hierarchy.
     */
    public void checkCanDelete(User actor, User target) {
        boolean allowed = target.isRootUser() ? actor.equals(target) : isInHierarchyOf(actor, target);
        if (!allowed) {
            deny(actor, "delete", target);
        }
    }

    private boolean isInHierarchyOf(User actor, User target) {
        for (User current = target; current != null; current = current.getCreatedBy()) {
            if (current.equals(actor)) {
                return true;
            }
        }
        return false;
    }

    private void requirePermission(User actor, PermissionAction action) {
        String permission = IAM_SERVICE_NAME + ":" + action.name();
        if (!permissionService.hasPermission(permission)) {
            log.warn("User '{}' lacks permission {}", actor.getUsername(), permission);
            throw new NoAccessException(NO_PERMISSION);
        }
    }

    private void deny(User actor, String operation, User target) {
        log.warn("User '{}' is not allowed to {} user '{}'", actor.getUsername(), operation, target.getUsername());
        throw new NoAccessException(NO_PERMISSION);
    }
}
