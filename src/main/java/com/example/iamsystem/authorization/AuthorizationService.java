package com.example.iamsystem.authorization;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.permission.PermissionKey;
import com.example.iamsystem.permission.repository.PermissionRepository;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.tenant.entity.TenantType;
import com.example.iamsystem.tenant.repository.TenantRepository;
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
    private final TenantRepository tenantRepository;

    /**
     * A root user holds every TENANT permission implicitly; the platform tenant's root also holds every PLATFORM
     * permission, a standard tenant's root never one. Everyone else holds the permissions of their roles and their
     * directly granted permissions. Inside another tenant ({@code X-Tenant-Id}) only the platform root holds anything.
     */
    public boolean hasPermission(AuthenticatedActor actor, PermissionKey permission) {
        if (actor.isActingInOtherTenant()) {
            return isPlatformRoot(actor);
        }
        if (actor.rootUser()) {
            return permission.scope() == TENANT || isPlatformRoot(actor);
        }
        return permissionRepository.isGrantedToUser(actor.userId(), permission.key());
    }

    private boolean isPlatformRoot(AuthenticatedActor actor) {
        return actor.rootUser() && tenantRepository.existsByIdAndType(actor.homeTenantId(), TenantType.PLATFORM);
    }

    public void checkPermission(AuthenticatedActor actor, PermissionKey permission) {
        if (!hasPermission(actor, permission)) {
            log.warn("User '{}' lacks permission {}", actor.username(), permission.key());
            throw new NoAccessException(NO_PERMISSION);
        }
    }
}
