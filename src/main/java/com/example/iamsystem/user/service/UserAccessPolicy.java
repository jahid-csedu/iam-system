package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.example.iamsystem.common.exception.ErrorMessage.NO_PERMISSION;
import static com.example.iamsystem.common.exception.ErrorMessage.OWN_ROLES_CHANGE;

/**
 * Decides what an actor may do to another user of the same tenant. Endpoint permissions are checked separately by
 * {@code @RequirePermission}, and the tenant boundary by the tenant-scoped lookups in {@code UserRepository}.
 */
@Component
@Slf4j
public class UserAccessPolicy {

    /**
     * Nobody changes their own roles; another user with the permission has to do it.
     */
    public void checkCanManageRoles(User actor, User target) {
        if (actor.equals(target)) {
            log.warn("User '{}' tried to change their own roles", actor.getUsername());
            throw new NoAccessException(OWN_ROLES_CHANGE);
        }
    }

    /**
     * A user may change their own password; the root user may also change anyone else's password.
     */
    public void checkCanChangePassword(User actor, User target) {
        if (!actor.equals(target) && !actor.isRootUser()) {
            deny(actor, "change the password of", target);
        }
    }

    /**
     * The root user can only be deleted by themselves.
     */
    public void checkCanDelete(User actor, User target) {
        if (target.isRootUser() && !actor.equals(target)) {
            deny(actor, "delete", target);
        }
    }

    private void deny(User actor, String operation, User target) {
        log.warn("User '{}' is not allowed to {} user '{}'", actor.getUsername(), operation, target.getUsername());
        throw new NoAccessException(NO_PERMISSION);
    }
}
