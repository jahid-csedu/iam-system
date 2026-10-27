package com.example.iamsystem.role.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.common.exception.AlreadyExistsException;
import com.example.iamsystem.role.dto.RoleDto;
import com.example.iamsystem.role.dto.RoleRequestDto;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.mapper.RoleMapper;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.iamsystem.common.exception.ErrorMessage.ROLE_EXISTS;

/**
 * Creates, reads, updates and deletes the roles of the current user's tenant.
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final RoleAccessPolicy accessPolicy;
    private final CurrentUserProvider currentUserProvider;
    private final RoleMapper roleMapper;

    @Auditable(
            value = AuditEventType.ROLE_CREATION,
            target = "#request.name()",
            detailsExpression = "T(java.util.Map).of('role_id', #result.id(), 'role_name', #result.name())"
    )
    @Transactional
    public RoleDto createRole(RoleRequestDto request) {
        Long tenantId = currentUserProvider.requireCurrentTenantId();
        if (roleRepository.existsByNameAndTenantId(request.name(), tenantId)) {
            throw new AlreadyExistsException(ROLE_EXISTS);
        }
        Role role = roleMapper.toEntity(request);
        role.setTenant(tenantRepository.getReferenceById(tenantId));
        return roleMapper.toDto(roleRepository.save(role));
    }

    @Auditable(
            value = AuditEventType.ROLE_UPDATE,
            target = "#request.name()",
            detailsExpression = "T(java.util.Map).of('role_id', #id, 'role_name', #request.name())"
    )
    @Transactional
    public RoleDto updateRole(Long id, RoleRequestDto request) {
        AuthenticatedActor actor = currentUserProvider.requireCurrentActor();
        Long tenantId = actor.tenantId();
        Role role = roleRepository.getRequiredByIdAndTenantId(id, tenantId);
        accessPolicy.checkCanModify(actor, role);
        if (roleRepository.existsByNameAndTenantIdAndIdNot(request.name(), tenantId, id)) {
            throw new AlreadyExistsException(ROLE_EXISTS);
        }
        roleMapper.updateEntity(role, request);
        return roleMapper.toDto(roleRepository.save(role));
    }

    /**
     * Deleting a role also removes it from every user holding it.
     */
    @Auditable(
            value = AuditEventType.ROLE_DELETE,
            target = "#id"
    )
    @Transactional
    public void deleteRole(Long id) {
        AuthenticatedActor actor = currentUserProvider.requireCurrentActor();
        Role role = roleRepository.getRequiredByIdAndTenantId(id, actor.tenantId());
        accessPolicy.checkCanModify(actor, role);
        roleRepository.delete(role);
    }

    @Transactional(readOnly = true)
    public RoleDto getRole(Long id) {
        return roleMapper.toDto(roleRepository.getRequiredByIdAndTenantId(id, currentUserProvider.requireCurrentTenantId()));
    }

    @Transactional(readOnly = true)
    public RoleDto getRoleByName(String name) {
        return roleMapper.toDto(roleRepository.getRequiredByNameAndTenantId(name, currentUserProvider.requireCurrentTenantId()));
    }

    @Transactional(readOnly = true)
    public List<RoleDto> getRoles() {
        return roleMapper.toDtoList(roleRepository.findAllByTenantIdOrderByName(currentUserProvider.requireCurrentTenantId()));
    }
}
