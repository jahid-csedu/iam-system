package com.example.iamsystem.authorization;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.permission.entity.PermissionAction;
import com.example.iamsystem.permission.repository.PermissionRepository;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.example.iamsystem.common.exception.ErrorMessage.NO_PERMISSION;

/**
 * The single place that decides whether a user holds a permission. Permissions are read from the database on every
 * check, so role and permission changes apply immediately.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {

    private final PermissionRepository permissionRepository;

    /**
     * Root users hold every permission; everyone else holds the permissions of their roles.
     */
    public boolean hasPermission(AuthenticatedActor actor, String serviceName, PermissionAction action) {
        return actor.rootUser() || permissionRepository.isGrantedToUser(actor.userId(), serviceName, action);
    }

    public void checkPermission(AuthenticatedActor actor, String serviceName, PermissionAction action) {
        if (!hasPermission(actor, serviceName, action)) {
            log.warn("User '{}' lacks permission {}:{}", actor.username(), serviceName, action);
            throw new NoAccessException(NO_PERMISSION);
        }
    }
}
