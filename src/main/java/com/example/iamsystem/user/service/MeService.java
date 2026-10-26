package com.example.iamsystem.user.service;

import com.example.iamsystem.authorization.AuthorizationService;
import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.permission.PermissionKey;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.tenant.dto.TenantDto;
import com.example.iamsystem.tenant.mapper.TenantMapper;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.MeDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.iamsystem.common.exception.ErrorMessage.TENANT_NOT_FOUND;

/**
 * Who the logged-in management user is and what they may do in the tenant the request works in. Regular users never
 * use the management console, so they get no answer.
 */
@Service
@RequiredArgsConstructor
public class MeService {

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final AuthorizationService authorizationService;
    private final TenantMapper tenantMapper;
    private final UserAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public MeDto currentUser() {
        AuthenticatedActor actor = currentUserProvider.requireCurrentActor();
        User user = userRepository.getRequiredByIdAndTenantId(actor.userId(), actor.homeTenantId());
        accessPolicy.checkIsManagementUser(user);
        TenantDto homeTenant = tenantMapper.toDto(user.getTenant());
        TenantDto workingTenant = actor.isActingInOtherTenant()
                ? tenantMapper.toDto(tenantRepository.findById(actor.tenantId())
                        .orElseThrow(() -> new DataNotFoundException(TENANT_NOT_FOUND)))
                : homeTenant;
        List<String> permissions = authorizationService.permissionsOf(actor).stream().map(PermissionKey::key).sorted().toList();
        return new MeDto(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(), user.getUserType(),
                user.isRootUser(), homeTenant, workingTenant, permissions);
    }
}
