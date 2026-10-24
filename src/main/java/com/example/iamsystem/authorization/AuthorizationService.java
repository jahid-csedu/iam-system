package com.example.iamsystem.authorization;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.permission.PermissionKey;
import com.example.iamsystem.permission.repository.PermissionRepository;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.example.iamsystem.common.exception.ErrorMessage.NO_PERMISSION;
import static com.example.iamsystem.permission.entity.PermissionScope.TENANT;

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
     * The root user holds every TENANT permission implicitly (never a PLATFORM one); everyone else holds the
     * permissions of their roles.
     */
    public boolean hasPermission(AuthenticatedActor actor, PermissionKey permission) {
        if (actor.rootUser()) {
            return permission.scope() == TENANT;
        }
        return permissionRepository.isGrantedToUser(actor.userId(), permission.key());
    }

    public void checkPermission(AuthenticatedActor actor, PermissionKey permission) {
        if (!hasPermission(actor, permission)) {
            log.warn("User '{}' lacks permission {}", actor.username(), permission.key());
            throw new NoAccessException(NO_PERMISSION);
        }
    }
}
