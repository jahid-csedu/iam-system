package com.example.iamsystem.role.service;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.example.iamsystem.common.exception.ErrorMessage.HELD_ROLE_CHANGE;

/**
 * Decides whether an actor may change a role of their tenant. Endpoint permissions are checked separately by
 * {@code @RequirePermission}, and the tenant boundary by the tenant-scoped lookups in {@code RoleRepository}.
 */
@Component
@Slf4j
public class RoleAccessPolicy {

    /**
     * Nobody changes or deletes a role they hold; another user with the permission has to do it.
     */
    public void checkCanModify(User actor, Role role) {
        if (actor.getRoles().contains(role)) {
            log.warn("User '{}' tried to change role '{}', which they hold", actor.getUsername(), role.getName());
            throw new NoAccessException(HELD_ROLE_CHANGE);
        }
    }
}
