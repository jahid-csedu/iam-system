package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.example.iamsystem.common.exception.ErrorMessage.MANAGEMENT_USERS_ONLY;
import static com.example.iamsystem.common.exception.ErrorMessage.NO_PERMISSION;
import static com.example.iamsystem.common.exception.ErrorMessage.OWN_ACCESS_CHANGE;
import static com.example.iamsystem.common.exception.ErrorMessage.OWN_ACCOUNT_NOT_DELETABLE;
import static com.example.iamsystem.common.exception.ErrorMessage.OWN_PASSWORD_RESET;
import static com.example.iamsystem.common.exception.ErrorMessage.OWN_PROFILE_UPDATE;
import static com.example.iamsystem.common.exception.ErrorMessage.OWN_STATUS_CHANGE;
import static com.example.iamsystem.common.exception.ErrorMessage.REGULAR_USER_ACCESS;
import static com.example.iamsystem.common.exception.ErrorMessage.ROOT_PROFILE_UPDATE;
import static com.example.iamsystem.common.exception.ErrorMessage.ROOT_STATUS_CHANGE;
import static com.example.iamsystem.common.exception.ErrorMessage.ROOT_USER_NOT_DELETABLE;

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
     * The self-service of {@code /api/me} is for management users: regular users never use the management console.
     */
    public void checkIsManagementUser(User user) {
        if (user.getUserType() != UserType.MANAGEMENT) {
            throw new NoAccessException(MANAGEMENT_USERS_ONLY);
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
     * Another user's profile only: your own goes through {@code /api/me} (which asks for your password before an email
     * change), and the root user's only through the root itself, since its email receives its password resets.
     */
    public void checkCanUpdateProfile(AuthenticatedActor actor, User target) {
        if (actor.isUser(target.getId())) {
            throw new NoAccessException(OWN_PROFILE_UPDATE);
        }
        if (target.isRootUser()) {
            log.warn("User '{}' tried to change the profile of the root user '{}'", actor.username(), target.getUsername());
            throw new NoAccessException(ROOT_PROFILE_UPDATE);
        }
    }

    /**
     * Nobody deletes a root user, not even the root itself: a tenant always keeps its root, and nothing can create
     * another one.
     */
    public void checkCanDelete(AuthenticatedActor actor, User target) {
        if (target.isRootUser()) {
            log.warn("User '{}' tried to delete the root user '{}'", actor.username(), target.getUsername());
            throw new NoAccessException(ROOT_USER_NOT_DELETABLE);
        }
        if (actor.isUser(target.getId())) {
            log.warn("User '{}' tried to delete their own account", actor.username());
            throw new NoAccessException(OWN_ACCOUNT_NOT_DELETABLE);
        }
    }

    /**
     * Deactivating and activating is always for someone else, and never for the root user: a tenant's root stays usable,
     * so the tenant can't be locked out of its own management.
     */
    public void checkCanChangeStatus(AuthenticatedActor actor, User target) {
        if (actor.isUser(target.getId())) {
            throw new NoAccessException(OWN_STATUS_CHANGE);
        }
        if (target.isRootUser()) {
            log.warn("User '{}' tried to change the status of the root user '{}'", actor.username(), target.getUsername());
            throw new NoAccessException(ROOT_STATUS_CHANGE);
        }
    }

    private void deny(AuthenticatedActor actor, String operation, User target) {
        log.warn("User '{}' is not allowed to {} user '{}'", actor.username(), operation, target.getUsername());
        throw new NoAccessException(NO_PERMISSION);
    }
}
