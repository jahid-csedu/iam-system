package com.example.iamsystem.authorization;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.entity.PermissionAction;
import com.example.iamsystem.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.example.iamsystem.common.exception.ErrorMessage.NO_PERMISSION;

/**
 * The single place that decides whether a user holds a permission.
 */
@Service
@Slf4j
public class AuthorizationService {

    /**
     * Root users hold every permission; everyone else holds the permissions of their roles.
     */
    public boolean hasPermission(User actor, String serviceName, PermissionAction action) {
        if (actor.isRootUser()) {
            return true;
        }
        return actor.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> matches(permission, serviceName, action));
    }

    public void checkPermission(User actor, String serviceName, PermissionAction action) {
        if (!hasPermission(actor, serviceName, action)) {
            log.warn("User '{}' lacks permission {}:{}", actor.getUsername(), serviceName, action);
            throw new NoAccessException(NO_PERMISSION);
        }
    }

    private static boolean matches(Permission permission, String serviceName, PermissionAction action) {
        return serviceName.equals(permission.getServiceName()) && action == permission.getAction();
    }
}
