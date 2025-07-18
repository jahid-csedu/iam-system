package com.example.iamsystem.permission;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.exception.DataNotFoundException;
import com.example.iamsystem.exception.PermissionAlreadyExistsException;
import com.example.iamsystem.permission.model.Permission;
import com.example.iamsystem.permission.model.PermissionAction;
import com.example.iamsystem.permission.model.PermissionDto;
import com.example.iamsystem.permission.model.PermissionMapper;
import com.example.iamsystem.security.user.DefaultUserDetails;
import com.example.iamsystem.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static com.example.iamsystem.constant.ErrorMessage.PERMISSION_EXISTS;
import static com.example.iamsystem.constant.ErrorMessage.PERMISSION_NOT_FOUND;

@Service
@RequiredArgsConstructor()
@Slf4j
public class PermissionService {
    private final PermissionRepository permissionRepository;
    private static final PermissionMapper permissionMapper = Mappers.getMapper(PermissionMapper.class);
    private static final String PERMISSION_TEMPLATE = "%s:%s";

    @Auditable(
            value = AuditEventType.PERMISSION_CREATE,
            target = "#permissionDto.serviceName + ':' + #permissionDto.action",
            detailsExpression = "T(java.util.Map).of('permission_id', #result.id, 'permission_name', #result.serviceName + ':' + #result.action)"
    )
    public PermissionDto savePermission(PermissionDto permissionDto) {
        log.debug("Attempting to save permission: {}", permissionDto.getServiceName() + ":" + permissionDto.getAction());
        validateDuplicatePermission(permissionDto);
        Permission entity = permissionMapper.toEntity(permissionDto);
        Permission permission = permissionRepository.save(entity);
        log.info("Permission saved successfully with ID: {}", permission.getId());
        return permissionMapper.toDto(permission);
    }

    @Auditable(
            value = AuditEventType.PERMISSION_UPDATE,
            target = "#permissionDto.serviceName + ':' + #permissionDto.action",
            detailsExpression = "T(java.util.Map).of('permission_id', #id, 'permission_name', #permissionDto.serviceName + ':' + #permissionDto.action)"
    )
    public PermissionDto updatePermission(Long id, PermissionDto permissionDto) {
        log.debug("Attempting to update permission with ID: {}", id);
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Permission not found for update with ID: {}", id);
                    return new DataNotFoundException(PERMISSION_NOT_FOUND);
                });
        permissionMapper.toUpdateEntity(permission, permissionDto);
        Permission updatedPermission = permissionRepository.save(permission);
        log.info("Permission with ID: {} updated successfully", id);
        return permissionMapper.toDto(updatedPermission);
    }

    @Auditable(
            value = AuditEventType.PERMISSION_DELETE,
            target = "#id"
    )
    public void deletePermissionById(Long id) {
        log.debug("Attempting to delete permission by ID: {}", id);
        permissionRepository.deleteById(id);
        log.info("Permission with ID: {} deleted successfully", id);
    }

    public PermissionDto getPermissionById(Long id) {
        log.debug("Attempting to retrieve permission by ID: {}", id);
        return permissionRepository.findById(id)
                .map(permissionMapper::toDto)
                .orElseThrow(() -> {
                    log.warn("Permission not found with ID: {}", id);
                    return new DataNotFoundException(PERMISSION_NOT_FOUND);
                });
    }

    public List<PermissionDto> getPermissionByServiceName(String name) {
        log.debug("Attempting to retrieve permissions by service name: {}", name);
        List<PermissionDto> permissions = permissionMapper.toDto(permissionRepository.findAllByServiceName(name));
        log.info("Retrieved {} permissions for service name: {}", permissions.size(), name);
        return permissions;
    }

    public List<PermissionDto> getAllPermissions() {
        log.debug("Attempting to retrieve all permissions");
        List<PermissionDto> permissions = permissionMapper.toDto(permissionRepository.findAll());
        log.info("Retrieved {} total permissions", permissions.size());
        return permissions;
    }

    public boolean hasPermission(String requiredPermission) {
        log.debug("Checking if current user has permission: {}", requiredPermission);
        User user = getCurrentUser();
        if(Objects.isNull(user)) {
            log.error("Non authenticated user trying to access: {}", requiredPermission);
            return false;
        }
        if (user.isRootUser()) {
            log.info("Root user has all permissions. Granting access for: {}", requiredPermission);
            return true;
        }
        boolean hasPermission = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> String.format(PERMISSION_TEMPLATE, permission.getServiceName(), permission.getAction()))
                .anyMatch(requiredPermission::equals);
        if (hasPermission) {
            log.info("User '{}' has permission: {}", user.getUsername(), requiredPermission);
        } else {
            log.warn("User '{}' does NOT have permission: {}", user.getUsername(), requiredPermission);
        }
        return hasPermission;
    }

    private void validateDuplicatePermission(PermissionDto permissionDto) {
        log.debug("Validating for duplicate permission: {}", permissionDto.getServiceName() + ":" + permissionDto.getAction());
        PermissionAction action = PermissionAction.valueOf(permissionDto.getAction());
        permissionRepository.findByServiceNameAndAction(permissionDto.getServiceName(), action)
                .ifPresent(permission -> {
                    log.warn("Attempted to create duplicate permission: {}", permissionDto.getServiceName() + ":" + permissionDto.getAction());
                    throw new PermissionAlreadyExistsException(PERMISSION_EXISTS);
                });
        log.debug("No duplicate permission found for: {}", permissionDto.getServiceName() + ":" + permissionDto.getAction());
    }

    private User getCurrentUser() {
        log.debug("Attempting to retrieve current authenticated user");
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof DefaultUserDetails userDetails) {
            log.debug("Current user retrieved: {}", userDetails.getUsername());
            return userDetails.user();
        }
        log.warn("No authenticated user found in security context");
        return null;
    }
}
