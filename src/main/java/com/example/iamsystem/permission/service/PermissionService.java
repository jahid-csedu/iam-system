package com.example.iamsystem.permission.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.PermissionAlreadyExistsException;
import com.example.iamsystem.permission.dto.PermissionDto;
import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.entity.PermissionAction;
import com.example.iamsystem.permission.mapper.PermissionMapper;
import com.example.iamsystem.permission.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.iamsystem.common.exception.ErrorMessage.PERMISSION_EXISTS;
import static com.example.iamsystem.common.exception.ErrorMessage.PERMISSION_NOT_FOUND;

/**
 * Manages the permission catalog. Permission checks live in {@code AuthorizationService}.
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    @Auditable(
            value = AuditEventType.PERMISSION_CREATE,
            target = "#request.serviceName + ':' + #request.action",
            detailsExpression = "T(java.util.Map).of('permission_id', #result.id, 'permission_name', #result.serviceName + ':' + #result.action)"
    )
    @Transactional
    public PermissionDto savePermission(PermissionDto request) {
        ensureUnique(request, null);
        Permission saved = permissionRepository.save(permissionMapper.toEntity(request));
        return permissionMapper.toDto(saved);
    }

    @Auditable(
            value = AuditEventType.PERMISSION_UPDATE,
            target = "#request.serviceName + ':' + #request.action",
            detailsExpression = "T(java.util.Map).of('permission_id', #id, 'permission_name', #request.serviceName + ':' + #request.action)"
    )
    @Transactional
    public PermissionDto updatePermission(Long id, PermissionDto request) {
        Permission permission = getRequiredPermission(id);
        ensureUnique(request, id);
        permissionMapper.toUpdateEntity(permission, request);
        return permissionMapper.toDto(permissionRepository.save(permission));
    }

    @Auditable(
            value = AuditEventType.PERMISSION_DELETE,
            target = "#id"
    )
    @Transactional
    public void deletePermissionById(Long id) {
        permissionRepository.delete(getRequiredPermission(id));
    }

    @Transactional(readOnly = true)
    public PermissionDto getPermissionById(Long id) {
        return permissionMapper.toDto(getRequiredPermission(id));
    }

    @Transactional(readOnly = true)
    public List<PermissionDto> getPermissionByServiceName(String serviceName) {
        return permissionMapper.toDto(permissionRepository.findAllByServiceName(serviceName));
    }

    @Transactional(readOnly = true)
    public List<PermissionDto> getAllPermissions() {
        return permissionMapper.toDto(permissionRepository.findAll());
    }

    private Permission getRequiredPermission(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException(PERMISSION_NOT_FOUND));
    }

    /**
     * A service/action pair may exist only once; {@code currentId} is the permission being updated, if any.
     */
    private void ensureUnique(PermissionDto request, Long currentId) {
        PermissionAction action = PermissionAction.valueOf(request.getAction());
        permissionRepository.findByServiceNameAndAction(request.getServiceName(), action)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new PermissionAlreadyExistsException(PERMISSION_EXISTS);
                });
    }
}
