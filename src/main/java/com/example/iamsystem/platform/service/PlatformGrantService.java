package com.example.iamsystem.platform.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.repository.PermissionRepository;
import com.example.iamsystem.platform.dto.PermissionGrantDto;
import com.example.iamsystem.platform.dto.PermissionGrantRequestDto;
import com.example.iamsystem.platform.dto.PlatformGrantsDto;
import com.example.iamsystem.platform.dto.RoleGrantDto;
import com.example.iamsystem.platform.dto.RoleGrantRequestDto;
import com.example.iamsystem.platform.entity.PlatformPermissionGrant;
import com.example.iamsystem.platform.entity.PlatformRoleGrant;
import com.example.iamsystem.platform.mapper.PlatformGrantMapper;
import com.example.iamsystem.platform.repository.PlatformPermissionGrantRepository;
import com.example.iamsystem.platform.repository.PlatformRoleGrantRepository;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantType;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import com.example.iamsystem.user.service.UserAccessPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.example.iamsystem.common.exception.ErrorMessage.NO_GRANTS_ON_PLATFORM_TENANT;
import static com.example.iamsystem.common.exception.ErrorMessage.NO_PERMISSION;
import static com.example.iamsystem.common.exception.ErrorMessage.PLATFORM_ROOT_HOLDS_EVERYTHING;
import static com.example.iamsystem.common.exception.ErrorMessage.ROLES_NOT_FOUND;
import static com.example.iamsystem.common.exception.ErrorMessage.TENANT_NOT_FOUND;

/**
 * Gives and removes the grants that let platform users work in other tenants. Grants are managed from inside the
 * platform tenant (the endpoints' {@code users:read} / {@code users:update} are held there), only for its management
 * users other than the root, never for yourself, only with the platform tenant's roles or catalog permissions, and only
 * on standard tenants (or on all of them). Several roles or permissions are granted or removed at once, all or nothing
 * (every id must exist); a grant the user already has is kept as it is, and removing one they don't have changes nothing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformGrantService {

    private final PlatformRoleGrantRepository roleGrantRepository;
    private final PlatformPermissionGrantRepository permissionGrantRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserAccessPolicy accessPolicy;
    private final CurrentUserProvider currentUserProvider;
    private final PlatformGrantMapper grantMapper;

    @Transactional(readOnly = true)
    public PlatformGrantsDto findGrants(Long userId) {
        Long platformId = requirePlatformTenantId();
        User user = userRepository.getRequiredByIdAndTenantId(userId, platformId);
        return new PlatformGrantsDto(grantMapper.toRoleGrantDtos(roleGrantRepository.findAllByUserIdOrderById(user.getId())),
                grantMapper.toPermissionGrantDtos(permissionGrantRepository.findAllByUserIdOrderById(user.getId())));
    }

    /**
     * @return the user's grants of these roles on that tenant (or on all tenants), new and already held
     */
    @Auditable(
            value = AuditEventType.PLATFORM_ROLE_GRANTED,
            target = "#request.userId()",
            detailsExpression = "T(java.util.Map).of('role_ids', #request.roleIds(), 'tenant_id', #request.tenantId() == null ? 'ALL' : #request.tenantId())"
    )
    @Transactional
    public List<RoleGrantDto> grantRoles(RoleGrantRequestDto request) {
        Long platformId = requirePlatformTenantId();
        User user = getGrantableUser(request.userId(), platformId);
        Tenant tenant = grantedTenant(request.tenantId());
        List<PlatformRoleGrant> grants = getPlatformRoles(request.roleIds(), platformId).stream()
                .map(role -> roleGrantRepository.findByUserIdAndRoleIdAndTenantId(user.getId(), role.getId(), request.tenantId())
                        .orElseGet(() -> roleGrantRepository.save(newRoleGrant(user, role, tenant))))
                .toList();
        return grantMapper.toRoleGrantDtos(grants);
    }

    /**
     * Removes the user's grants of these roles on that tenant (or their grants on all tenants).
     */
    @Auditable(
            value = AuditEventType.PLATFORM_ROLE_GRANT_REVOKED,
            target = "#request.userId()",
            detailsExpression = "T(java.util.Map).of('role_ids', #request.roleIds(), 'tenant_id', #request.tenantId() == null ? 'ALL' : #request.tenantId())"
    )
    @Transactional
    public void revokeRoles(RoleGrantRequestDto request) {
        Long platformId = requirePlatformTenantId();
        User user = getGrantableUser(request.userId(), platformId);
        grantedTenant(request.tenantId());
        getPlatformRoles(request.roleIds(), platformId);
        roleGrantRepository.deleteAll(
                roleGrantRepository.findAllByUserIdAndRoleIdInAndTenantId(user.getId(), request.roleIds(), request.tenantId()));
    }

    /**
     * @return the user's grants of these permissions on that tenant (or on all tenants), new and already held
     */
    @Auditable(
            value = AuditEventType.PLATFORM_PERMISSION_GRANTED,
            target = "#request.userId()",
            detailsExpression = "T(java.util.Map).of('permission_ids', #request.permissionIds(), 'tenant_id', #request.tenantId() == null ? 'ALL' : #request.tenantId())"
    )
    @Transactional
    public List<PermissionGrantDto> grantPermissions(PermissionGrantRequestDto request) {
        Long platformId = requirePlatformTenantId();
        User user = getGrantableUser(request.userId(), platformId);
        Tenant tenant = grantedTenant(request.tenantId());
        List<PlatformPermissionGrant> grants = permissionRepository.getRequiredPermissions(request.permissionIds()).stream()
                .map(permission -> permissionGrantRepository.findByUserIdAndPermissionIdAndTenantId(user.getId(), permission.getId(), request.tenantId())
                        .orElseGet(() -> permissionGrantRepository.save(newPermissionGrant(user, permission, tenant))))
                .toList();
        return grantMapper.toPermissionGrantDtos(grants);
    }

    /**
     * Removes the user's grants of these permissions on that tenant (or their grants on all tenants).
     */
    @Auditable(
            value = AuditEventType.PLATFORM_PERMISSION_GRANT_REVOKED,
            target = "#request.userId()",
            detailsExpression = "T(java.util.Map).of('permission_ids', #request.permissionIds(), 'tenant_id', #request.tenantId() == null ? 'ALL' : #request.tenantId())"
    )
    @Transactional
    public void revokePermissions(PermissionGrantRequestDto request) {
        Long platformId = requirePlatformTenantId();
        User user = getGrantableUser(request.userId(), platformId);
        grantedTenant(request.tenantId());
        permissionRepository.getRequiredPermissions(request.permissionIds());
        permissionGrantRepository.deleteAll(permissionGrantRepository.findAllByUserIdAndPermissionIdInAndTenantId(
                user.getId(), request.permissionIds(), request.tenantId()));
    }

    /**
     * Grants are managed only by users working in the platform tenant; a standard tenant's admins never see them.
     */
    private Long requirePlatformTenantId() {
        AuthenticatedActor actor = currentUserProvider.requireCurrentActor();
        if (!tenantRepository.existsByIdAndType(actor.tenantId(), TenantType.PLATFORM)) {
            log.warn("User '{}' tried to manage platform grants outside the platform tenant", actor.username());
            throw new NoAccessException(NO_PERMISSION);
        }
        return actor.tenantId();
    }

    private User getGrantableUser(Long userId, Long platformId) {
        User user = userRepository.getRequiredByIdAndTenantId(userId, platformId);
        accessPolicy.checkCanManageAccess(currentUserProvider.requireCurrentActor(), user);
        accessPolicy.checkCanReceiveAccess(user);
        if (user.isRootUser()) {
            throw new UserInputNotValidException(PLATFORM_ROOT_HOLDS_EVERYTHING);
        }
        return user;
    }

    /**
     * All of these roles, each a role of the platform tenant; otherwise nothing is granted.
     */
    private List<Role> getPlatformRoles(Set<Long> roleIds, Long platformId) {
        List<Role> roles = roleRepository.findAllByIdInAndTenantId(roleIds, platformId);
        if (roles.size() != roleIds.size()) {
            throw new DataNotFoundException(ROLES_NOT_FOUND);
        }
        return roles;
    }

    /**
     * The standard tenant a grant applies to, or {@code null} for all tenants.
     */
    private Tenant grantedTenant(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new DataNotFoundException(TENANT_NOT_FOUND));
        if (tenant.isPlatform()) {
            throw new UserInputNotValidException(NO_GRANTS_ON_PLATFORM_TENANT);
        }
        return tenant;
    }

    private static PlatformRoleGrant newRoleGrant(User user, Role role, Tenant tenant) {
        PlatformRoleGrant grant = new PlatformRoleGrant();
        grant.setUser(user);
        grant.setRole(role);
        grant.setTenant(tenant);
        grant.setCreatedAt(Instant.now());
        return grant;
    }

    private static PlatformPermissionGrant newPermissionGrant(User user, Permission permission, Tenant tenant) {
        PlatformPermissionGrant grant = new PlatformPermissionGrant();
        grant.setUser(user);
        grant.setPermission(permission);
        grant.setTenant(tenant);
        grant.setCreatedAt(Instant.now());
        return grant;
    }
}
