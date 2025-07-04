package com.example.iamsystem.permission;

import com.example.iamsystem.audit.AuditService;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.audit.enums.AuditOutcome;
import com.example.iamsystem.exception.DataNotFoundException;
import com.example.iamsystem.exception.PermissionAlreadyExistsException;
import com.example.iamsystem.permission.model.Permission;
import com.example.iamsystem.permission.model.PermissionAction;
import com.example.iamsystem.permission.model.PermissionDto;
import com.example.iamsystem.permission.model.PermissionMapper;
import com.example.iamsystem.security.user.DefaultUserDetails;
import com.example.iamsystem.user.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.example.iamsystem.constant.ErrorMessage.PERMISSION_EXISTS;
import static com.example.iamsystem.constant.ErrorMessage.PERMISSION_NOT_FOUND;

@Service
@RequiredArgsConstructor()
@Slf4j
public class PermissionService {
    public static final String UNKNOWN = "UNKNOWN";
    public static final String PERMISSION_ID = "permission_id";
    public static final String PERMISSION_NAME = "permission_name";
    public static final String REASON = "reason";
    public static final String ID = "ID: ";
    private final PermissionRepository permissionRepository;
    private final AuditService auditService;
    private final HttpServletRequest request;
    private static final PermissionMapper permissionMapper = Mappers.getMapper(PermissionMapper.class);
    private static final String PERMISSION_TEMPLATE = "%s:%s";

    public PermissionDto savePermission(PermissionDto permissionDto) {
        log.debug("Attempting to save permission: {}", permissionDto.getServiceName() + ":" + permissionDto.getAction());
        User currentUser = getCurrentUser();
        String actor = (currentUser != null) ? currentUser.getUsername() : UNKNOWN;
        Map<String, Object> commonDetails = auditService.getRequestDetails(request);

        try {
            validateDuplicatePermission(permissionDto);
            Permission entity = permissionMapper.toEntity(permissionDto);
            Permission permission = permissionRepository.save(entity);
            log.info("Permission saved successfully with ID: {}", permission.getId());

            Map<String, Object> details = new HashMap<>(commonDetails);
            details.put(PERMISSION_ID, permission.getId());
            details.put(PERMISSION_NAME, permission.getServiceName() + ":" + permission.getAction());
            auditService.logAuditEvent(AuditEventType.PERMISSION_CREATED, actor, permission.getServiceName() + ":" + permission.getAction(), AuditOutcome.SUCCESS, details, this.getClass().getSimpleName(), new Object(){}.getClass().getEnclosingMethod().getName());

            return permissionMapper.toDto(permission);
        } catch (Exception e) {
            log.error("Failed to save permission: {}", permissionDto.getServiceName() + ":" + permissionDto.getAction(), e);
            Map<String, Object> details = new HashMap<>(commonDetails);
            details.put(REASON, e.getMessage());
            auditService.logAuditEvent(AuditEventType.PERMISSION_CREATION_FAILED, actor, permissionDto.getServiceName() + ":" + permissionDto.getAction(), AuditOutcome.FAILURE, details, this.getClass().getSimpleName(), new Object(){}.getClass().getEnclosingMethod().getName());
            throw e;
        }
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

    public PermissionDto getPermissionById(Long id) {
        log.debug("Attempting to retrieve permission by ID: {}", id);
        return permissionRepository.findById(id)
                .map(permissionMapper::toDto)
                .orElseThrow(() -> {
                    log.warn("Permission not found with ID: {}", id);
                    return new DataNotFoundException(PERMISSION_NOT_FOUND);
                });
    }

    public PermissionDto updatePermission(Long id, PermissionDto permissionDto) {
        log.debug("Attempting to update permission with ID: {}", id);
        String actor = (getCurrentUser() != null) ? getCurrentUser().getUsername() : UNKNOWN;
        Map<String, Object> commonDetails = auditService.getRequestDetails(request);

        try {
            Permission permission = permissionRepository.findById(id)
                    .orElseThrow(() -> {
                        log.warn("Permission not found for update with ID: {}", id);
                        return new DataNotFoundException(PERMISSION_NOT_FOUND);
                    });
            permissionMapper.toUpdateEntity(permission, permissionDto);
            Permission updatedPermission = permissionRepository.save(permission);
            log.info("Permission with ID: {} updated successfully", id);

            Map<String, Object> details = new HashMap<>(commonDetails);
            details.put(PERMISSION_ID, updatedPermission.getId());
            details.put(PERMISSION_NAME, updatedPermission.getServiceName() + ":" + updatedPermission.getAction());
            auditService.logAuditEvent(AuditEventType.PERMISSION_UPDATED, actor, updatedPermission.getServiceName() + ":" + updatedPermission.getAction(), AuditOutcome.SUCCESS, details, this.getClass().getSimpleName(), new Object(){}.getClass().getEnclosingMethod().getName());

            return permissionMapper.toDto(updatedPermission);
        } catch (Exception e) {
            log.error("Failed to update permission with ID: {}", id, e);
            Map<String, Object> details = new HashMap<>(commonDetails);
            details.put(REASON, e.getMessage());
            auditService.logAuditEvent(AuditEventType.PERMISSION_UPDATE_FAILED, actor, ID + id, AuditOutcome.FAILURE, details, this.getClass().getSimpleName(), new Object(){}.getClass().getEnclosingMethod().getName());
            throw e;
        }
    }

    public void deletePermissionById(Long id) {
        log.debug("Attempting to delete permission by ID: {}", id);
        String actor = (getCurrentUser() != null) ? getCurrentUser().getUsername() : UNKNOWN;
        Map<String, Object> commonDetails = auditService.getRequestDetails(request);

        try {
            permissionRepository.deleteById(id);
            log.info("Permission with ID: {} deleted successfully", id);

            Map<String, Object> details = new HashMap<>(commonDetails);
            details.put(PERMISSION_ID, id);
            auditService.logAuditEvent(AuditEventType.PERMISSION_DELETED, actor, ID + id, AuditOutcome.SUCCESS, details, this.getClass().getSimpleName(), new Object(){}.getClass().getEnclosingMethod().getName());

        } catch (Exception e) {
            log.error("Failed to delete permission with ID: {}", id, e);
            Map<String, Object> details = new HashMap<>(commonDetails);
            details.put(REASON, e.getMessage());
            auditService.logAuditEvent(AuditEventType.PERMISSION_DELETION_FAILED, actor, ID + id, AuditOutcome.FAILURE, details, this.getClass().getSimpleName(), new Object(){}.getClass().getEnclosingMethod().getName());
            throw e;
        }
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
