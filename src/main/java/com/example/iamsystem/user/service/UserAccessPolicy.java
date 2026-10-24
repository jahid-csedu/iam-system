package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.example.iamsystem.common.exception.ErrorMessage.NO_PERMISSION;
import static com.example.iamsystem.common.exception.ErrorMessage.OWN_PASSWORD_RESET;
import static com.example.iamsystem.common.exception.ErrorMessage.OWN_ACCESS_CHANGE;
import static com.example.iamsystem.common.exception.ErrorMessage.REGULAR_USER_ACCESS;

/**
 * Decides what an actor may do to another user of the same tenant. Endpoint permissions are checked separately by
 * {@code @RequirePermission}, and the tenant boundary by the tenant-scoped lookups in {@code UserRepository}.
 */
@Component
@Slf4j
public class UserAccessPolicy {

    /**
     * Nobody changes their own roles or direct permissions; another user with the permission has to do it.
     */
    public void checkCanManageAccess(AuthenticatedActor actor, User target) {
        if (actor.isUser(target.getId())) {
            log.warn("User '{}' tried to change their own roles or permissions", actor.username());
            throw new NoAccessException(OWN_ACCESS_CHANGE);
        }
    }

    /**
     * Only management users hold roles and permissions; regular users never do.
     */
    public void checkCanReceiveAccess(User target) {
        if (target.getUserType() != UserType.MANAGEMENT) {
            throw new UserInputNotValidException(REGULAR_USER_ACCESS);
        }
    }

    /**
     * A reset is always for someone else (your own password is changed, not reset), and never for the root user,
     * whose access nobody else may take over.
     */
    public void checkCanResetPassword(AuthenticatedActor actor, User target) {
        if (actor.isUser(target.getId())) {
            log.warn("User '{}' tried to reset their own password", actor.username());
            throw new NoAccessException(OWN_PASSWORD_RESET);
        }
        if (target.isRootUser()) {
            deny(actor, "reset the password of", target);
        }
    }

    /**
     * The root user can only be deleted by themselves.
     */
    public void checkCanDelete(AuthenticatedActor actor, User target) {
        if (target.isRootUser() && !actor.isUser(target.getId())) {
            deny(actor, "delete", target);
        }
    }

    private void deny(AuthenticatedActor actor, String operation, User target) {
        log.warn("User '{}' is not allowed to {} user '{}'", actor.username(), operation, target.getUsername());
        throw new NoAccessException(NO_PERMISSION);
    }
}
