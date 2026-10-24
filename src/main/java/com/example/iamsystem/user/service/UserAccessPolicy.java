package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.example.iamsystem.common.exception.ErrorMessage.NO_PERMISSION;
import static com.example.iamsystem.common.exception.ErrorMessage.USER_NOT_FOUND;

/**
 * Decides which users an actor may see and manage. Endpoint permissions are checked separately by {@code @RequirePermission}.
 * <p>
 * Today the boundary is the creator hierarchy: an actor may act on users they created, directly or indirectly.
 * Tenant isolation will replace this class's rules.
 */
@Component
@Slf4j
public class UserAccessPolicy {

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

    public void checkCanManageRoles(User actor, User target) {
        if (!isInHierarchyOf(actor, target)) {
            deny(actor, "manage roles of", target);
        }
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

    private void deny(User actor, String operation, User target) {
        log.warn("User '{}' is not allowed to {} user '{}'", actor.getUsername(), operation, target.getUsername());
        throw new NoAccessException(NO_PERMISSION);
    }
}
