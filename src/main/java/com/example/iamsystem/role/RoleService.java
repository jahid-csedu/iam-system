package com.example.iamsystem.role;

import com.example.iamsystem.audit.AuditService;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.audit.enums.AuditOutcome;
import com.example.iamsystem.exception.DataNotFoundException;
import com.example.iamsystem.permission.model.Permission;
import com.example.iamsystem.permission.PermissionRepository;
import com.example.iamsystem.role.model.Role;
import com.example.iamsystem.role.model.RoleDto;
import com.example.iamsystem.role.model.RoleMapper;
import com.example.iamsystem.role.model.RolePermissionDto;
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
import java.util.Set;
import java.util.stream.Collectors;

import static com.example.iamsystem.constant.ErrorMessage.ROLE_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {
    public static final String UNKNOWN = "UNKNOWN";
    public static final String ROLE_ID = "role_id";
    public static final String ROLE_NAME = "role_name";
    public static final String ID = "ID: ";
    public static final String PERMISSIONS_ASSIGNED = "permissions_assigned";
    public static final String REASON = "reason";
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditService auditService;
    private final HttpServletRequest request;
    private static final RoleMapper roleMapper = Mappers.getMapper(RoleMapper.class);

    public RoleDto createRole(RoleDto roleDto) {
        log.debug("Attempting to create role: {}", roleDto.getName());
        String actor = (getCurrentUser() != null) ? getCurrentUser().getUsername() : UNKNOWN;
        Map<String, Object> commonDetails = auditService.getRequestDetails(request);

        try {
            Role role = roleMapper.toEntity(roleDto);
            Role savedRole = roleRepository.save(role);
            log.info("Role created successfully with ID: {}", savedRole.getId());

            Map<String, Object> details = new HashMap<>(commonDetails);
            details.put(ROLE_ID, savedRole.getId());
            details.put(ROLE_NAME, savedRole.getName());
            auditService.logAuditEvent(AuditEventType.ROLE_CREATED, actor, savedRole.getName(), AuditOutcome.SUCCESS, details, this.getClass().getSimpleName(), new Object() {}.getClass().getEnclosingMethod().getName());

            return roleMapper.toDto(savedRole);
        } catch (Exception e) {
            log.error("Failed to create role: {}", roleDto.getName(), e);
            Map<String, Object> details = new HashMap<>(commonDetails);
            details.put(REASON, e.getMessage());
            auditService.logAuditEvent(AuditEventType.ROLE_CREATION_FAILED, actor, roleDto.getName(), AuditOutcome.FAILURE, details, this.getClass().getSimpleName(), new Object() {}.getClass().getEnclosingMethod().getName());
            throw e;
        }
    }

    public RoleDto updateRole(Long id, RoleDto roleDto) {
        log.debug("Attempting to update role with ID: {}", id);
        String actor = (getCurrentUser() != null) ? getCurrentUser().getUsername() : UNKNOWN;
        Map<String, Object> commonDetails = auditService.getRequestDetails(request);

        try {
            Role role = roleRepository.findById(id)
                    .orElseThrow(() -> {
                        log.warn("Role not found for update with ID: {}", id);
                        return new DataNotFoundException(ROLE_NOT_FOUND);
                    });
            roleMapper.toUpdateEntity(role, roleDto);
            Role updatedRole = roleRepository.save(role);
            log.info("Role with ID: {} updated successfully", id);

            Map<String, Object> details = new HashMap<>(commonDetails);
            details.put(ROLE_ID, updatedRole.getId());
            details.put(ROLE_NAME, updatedRole.getName());
            auditService.logAuditEvent(AuditEventType.ROLE_UPDATED, actor, updatedRole.getName(), AuditOutcome.SUCCESS, details, this.getClass().getSimpleName(), new Object() {}.getClass().getEnclosingMethod().getName());

            return roleMapper.toDto(updatedRole);
        } catch (Exception e) {
            log.error("Failed to update role with ID: {}", id, e);
            Map<String, Object> details = new HashMap<>(commonDetails);
            details.put(REASON, e.getMessage());
            auditService.logAuditEvent(AuditEventType.ROLE_UPDATE_FAILED, actor, ID + id, AuditOutcome.FAILURE, details, this.getClass().getSimpleName(), new Object() {}.getClass().getEnclosingMethod().getName());
            throw e;
        }
    }

    public void deleteRole(Long id) {
        log.debug("Attempting to delete role with ID: {}", id);
        String actor = (getCurrentUser() != null) ? getCurrentUser().getUsername() : UNKNOWN;
        Map<String, Object> commonDetails = auditService.getRequestDetails(request);

        try {
            roleRepository.deleteById(id);
            log.info("Role with ID: {} deleted successfully", id);

            Map<String, Object> details = new HashMap<>(commonDetails);
            details.put(ROLE_ID, id);
            auditService.logAuditEvent(AuditEventType.ROLE_DELETED, actor, ID + id, AuditOutcome.SUCCESS, details, this.getClass().getSimpleName(), new Object() {}.getClass().getEnclosingMethod().getName());

        } catch (Exception e) {
            log.error("Failed to delete role with ID: {}", id, e);
            Map<String, Object> details = new HashMap<>(commonDetails);
            details.put("reason", e.getMessage());
            auditService.logAuditEvent(AuditEventType.ROLE_DELETION_FAILED, actor, ID + id, AuditOutcome.FAILURE, details, this.getClass().getSimpleName(), new Object() {}.getClass().getEnclosingMethod().getName());
            throw e;
        }
    }


    public RoleDto getRole(Long id) {
        log.debug("Attempting to retrieve role by ID: {}", id);
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Role not found with ID: {}", id);
                    return new DataNotFoundException(ROLE_NOT_FOUND);
                });
        log.info("Successfully retrieved role with ID: {}", id);
        return roleMapper.toDto(role);
    }

    public RoleDto getRoleByName(String name) {
        log.debug("Attempting to retrieve role by name: {}", name);
        Role role = roleRepository.findByName(name)
                .orElseThrow(() -> {
                    log.warn("Role not found with name: {}", name);
                    return new DataNotFoundException(ROLE_NOT_FOUND);
                });
        log.info("Successfully retrieved role with name: {}", name);
        return roleMapper.toDto(role);
    }

    public List<RoleDto> getRoles() {
        log.debug("Attempting to retrieve all roles");
        List<Role> roles = roleRepository.findAll();
        log.info("Retrieved {} total roles", roles.size());
        return roleMapper.toDto(roles);
    }

    public void assignPermissions(RolePermissionDto rolePermissionDto) {
        log.debug("Attempting to assign permissions to role ID: {}", rolePermissionDto.getRoleId());
        String actor = (getCurrentUser() != null) ? getCurrentUser().getUsername() : UNKNOWN;
        Map<String, Object> commonDetails = auditService.getRequestDetails(request);

        try {
            Role role = findRoleById(rolePermissionDto);

            attachPermissionToRole(role, rolePermissionDto.getPermissionIds());
            roleRepository.save(role);
            log.info("Permissions assigned successfully to role ID: {}", rolePermissionDto.getRoleId());

            Map<String, Object> details = new HashMap<>(commonDetails);
            details.put(ROLE_ID, rolePermissionDto.getRoleId());
            details.put(PERMISSIONS_ASSIGNED, rolePermissionDto.getPermissionIds().stream().map(String::valueOf).collect(Collectors.joining(", ")));
            auditService.logAuditEvent(AuditEventType.PERMISSIONS_ASSIGNED_TO_ROLE, actor, ID + rolePermissionDto.getRoleId(), AuditOutcome.SUCCESS, details, this.getClass().getSimpleName(), new Object() {}.getClass().getEnclosingMethod().getName());

        } catch (Exception e) {
            log.error("Failed to assign permissions to role ID: {}", rolePermissionDto.getRoleId(), e);
            Map<String, Object> details = new HashMap<>(commonDetails);
            details.put(REASON, e.getMessage());
            auditService.logAuditEvent(AuditEventType.PERMISSIONS_ASSIGNMENT_TO_ROLE_FAILED, actor, ID + rolePermissionDto.getRoleId(), AuditOutcome.FAILURE, details, this.getClass().getSimpleName(), new Object() {}.getClass().getEnclosingMethod().getName());
            throw e;
        }
    }

    public void removePermissions(RolePermissionDto rolePermissionDto) {
        log.debug("Attempting to remove permissions from role ID: {}", rolePermissionDto.getRoleId());
        String actor = (getCurrentUser() != null) ? getCurrentUser().getUsername() : UNKNOWN;
        Map<String, Object> commonDetails = auditService.getRequestDetails(request);

        try {
            Role role = findRoleById(rolePermissionDto);

            detachPermissionFromRole(role, rolePermissionDto.getPermissionIds());
            roleRepository.save(role);
            log.info("Permissions removed successfully from role ID: {}", rolePermissionDto.getRoleId());

            Map<String, Object> details = new HashMap<>(commonDetails);
            details.put(ROLE_ID, rolePermissionDto.getRoleId());
            details.put("permissions_removed", rolePermissionDto.getPermissionIds().stream().map(String::valueOf).collect(Collectors.joining(", ")));
            auditService.logAuditEvent(AuditEventType.PERMISSIONS_REMOVED_FROM_ROLE, actor, ID + rolePermissionDto.getRoleId(), AuditOutcome.SUCCESS, details, this.getClass().getSimpleName(), new Object() {}.getClass().getEnclosingMethod().getName());
        } catch (Exception e) {
            log.error("Failed to remove permissions from role ID: {}", rolePermissionDto.getRoleId(), e);
            Map<String, Object> details = new HashMap<>(commonDetails);
            details.put(REASON, e.getMessage());
            auditService.logAuditEvent(AuditEventType.PERMISSIONS_REMOVED_FROM_ROLE_FAILED, actor, ID + rolePermissionDto.getRoleId(), AuditOutcome.FAILURE, details, this.getClass().getSimpleName(), new Object() {}.getClass().getEnclosingMethod().getName());
            throw e;
        }
    }

    private Role findRoleById(RolePermissionDto rolePermissionDto) {
        log.debug("Finding role by ID: {}", rolePermissionDto.getRoleId());
        return roleRepository.findById(rolePermissionDto.getRoleId())
                .orElseThrow(() -> {
                    log.warn("Role not found for permission assignment/removal with ID: {}", rolePermissionDto.getRoleId());
                    return new DataNotFoundException(ROLE_NOT_FOUND);
                });
    }

    private void attachPermissionToRole(Role role, Set<Long> permissionIds) {
        log.debug("Attaching permissions {} to role ID: {}", permissionIds, role.getId());
        List<Permission> permissions = getPermissions(permissionIds);

        role.getPermissions().addAll(permissions);
        log.debug("Permissions attached to role ID: {}", role.getId());
    }

    private void detachPermissionFromRole(Role role, Set<Long> permissionIds) {
        log.debug("Detaching permissions {} from role ID: {}", permissionIds, role.getId());
        List<Permission> permissions = getPermissions(permissionIds);

        permissions.forEach(role.getPermissions()::remove);
        log.debug("Permissions detached from role ID: {}", role.getId());
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

    private List<Permission> getPermissions(Set<Long> permissionIds) {
        log.debug("Retrieving permissions for IDs: {}", permissionIds);
        List<Permission> permissions = permissionRepository.findAllById(permissionIds);
        if (permissions.size() != permissionIds.size()) {
            log.warn("Some permissions not found for IDs: {}", permissionIds);
            throw new DataNotFoundException("Some permissions not found");
        }
        log.debug("Successfully retrieved {} permissions for IDs: {}", permissions.size(), permissionIds);
        return permissions;
    }
}
