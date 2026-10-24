package com.example.iamsystem.tenant.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.common.exception.TenantAlreadyExistsException;
import com.example.iamsystem.role.service.PredefinedRoleService;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.tenant.dto.TenantSignupResultDto;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.tenant.mapper.TenantMapper;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.mapper.UserMapper;
import com.example.iamsystem.user.repository.UserRepository;
import com.example.iamsystem.user.service.UserFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.iamsystem.common.exception.ErrorMessage.TENANT_EXISTS;

/**
 * Creates a tenant with its single root user and its predefined roles in one transaction: either all exist afterwards,
 * or none.
 */
@Service
@RequiredArgsConstructor
public class TenantSignupService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserFactory userFactory;
    private final PredefinedRoleService predefinedRoleService;
    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;

    @Auditable(
            value = AuditEventType.TENANT_SIGNUP,
            target = "#request.tenantName()",
            detailsExpression = "T(java.util.Map).of('tenant_id', #result.tenant().id(), 'root_username', #result.rootUser().username)"
    )
    @Transactional
    public TenantSignupResultDto signup(TenantSignupDto request) {
        if (tenantRepository.existsByName(request.tenantName())) {
            throw new TenantAlreadyExistsException(TENANT_EXISTS);
        }
        Tenant tenant = tenantRepository.save(new Tenant(request.tenantName(), TenantStatus.ACTIVE));
        predefinedRoleService.createPredefinedRoles(tenant);

        User root = userFactory.newUser(request.rootUser(), tenant);
        root.setRootUser(true);
        User savedRoot = userRepository.save(root);

        return new TenantSignupResultDto(tenantMapper.toDto(tenant), userMapper.toDto(savedRoot));
    }
}
