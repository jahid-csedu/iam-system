package com.example.iamsystem.platform.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.GrantAlreadyExistsException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.platform.dto.PlatformGrantsDto;
import com.example.iamsystem.platform.dto.RoleGrantDto;
import com.example.iamsystem.platform.dto.RoleGrantRequestDto;
import com.example.iamsystem.platform.entity.PlatformRoleGrant;
import com.example.iamsystem.platform.mapper.PlatformGrantMapper;
import com.example.iamsystem.platform.repository.PlatformRoleGrantRepository;
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

import static com.example.iamsystem.common.exception.ErrorMessage.GRANT_EXISTS;
import static com.example.iamsystem.common.exception.ErrorMessage.GRANT_NOT_FOUND;
import static com.example.iamsystem.common.exception.ErrorMessage.NO_GRANTS_ON_PLATFORM_TENANT;
import static com.example.iamsystem.common.exception.ErrorMessage.NO_PERMISSION;
import static com.example.iamsystem.common.exception.ErrorMessage.PLATFORM_ROOT_HOLDS_EVERYTHING;
import static com.example.iamsystem.common.exception.ErrorMessage.TENANT_NOT_FOUND;

/**
 * Gives and removes the grants that let platform users work in other tenants. Grants are managed from inside the
 * platform tenant (the endpoints' {@code users:read} / {@code users:update} are held there), only for its management
 * users other than the root, never for yourself, only with the platform tenant's roles, and only on standard tenants
 * (or on all of them).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformGrantService {

    private final PlatformRoleGrantRepository roleGrantRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserAccessPolicy accessPolicy;
    private final CurrentUserProvider currentUserProvider;
    private final PlatformGrantMapper grantMapper;

    @Transactional(readOnly = true)
    public PlatformGrantsDto findGrants(Long userId) {
        Long platformId = requirePlatformTenantId();
        User user = userRepository.getRequiredByIdAndTenantId(userId, platformId);
        return new PlatformGrantsDto(grantMapper.toRoleGrantDtos(roleGrantRepository.findAllByUserIdOrderById(user.getId())));
    }

    @Auditable(
            value = AuditEventType.PLATFORM_ROLE_GRANTED,
            target = "#request.userId()",
            detailsExpression = "T(java.util.Map).of('role_id', #request.roleId(), 'tenant_id', #request.tenantId() == null ? 'ALL' : #request.tenantId())"
    )
    @Transactional
    public RoleGrantDto grantRole(RoleGrantRequestDto request) {
        Long platformId = requirePlatformTenantId();
        User user = getGrantableUser(request.userId(), platformId);
        PlatformRoleGrant grant = new PlatformRoleGrant();
        grant.setUser(user);
        grant.setRole(roleRepository.getRequiredByIdAndTenantId(request.roleId(), platformId));
        grant.setTenant(grantedTenant(request.tenantId()));
        if (roleGrantRepository.existsByUserIdAndRoleIdAndTenantId(user.getId(), request.roleId(), request.tenantId())) {
            throw new GrantAlreadyExistsException(GRANT_EXISTS);
        }
        grant.setCreatedAt(Instant.now());
        return grantMapper.toDto(roleGrantRepository.save(grant));
    }

    @Auditable(value = AuditEventType.PLATFORM_ROLE_GRANT_REVOKED, target = "#grantId")
    @Transactional
    public void revokeRoleGrant(Long grantId) {
        requirePlatformTenantId();
        PlatformRoleGrant grant = roleGrantRepository.findById(grantId)
                .orElseThrow(() -> new DataNotFoundException(GRANT_NOT_FOUND));
        accessPolicy.checkCanManageAccess(currentUserProvider.requireCurrentActor(), grant.getUser());
        roleGrantRepository.delete(grant);
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
}
