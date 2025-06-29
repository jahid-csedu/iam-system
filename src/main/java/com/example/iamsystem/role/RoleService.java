package com.example.iamsystem.role;

import com.example.iamsystem.exception.DataNotFoundException;
import com.example.iamsystem.permission.model.Permission;
import com.example.iamsystem.permission.PermissionRepository;
import com.example.iamsystem.role.model.Role;
import com.example.iamsystem.role.model.RoleDto;
import com.example.iamsystem.role.model.RoleMapper;
import com.example.iamsystem.role.model.RolePermissionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

import static com.example.iamsystem.constant.ErrorMessage.ROLE_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private static final RoleMapper roleMapper = Mappers.getMapper(RoleMapper.class);

    public RoleDto createRole(RoleDto roleDto) {
        log.debug("Attempting to create role: {}", roleDto.getName());
        Role role = roleMapper.toEntity(roleDto);
        Role savedRole = roleRepository.save(role);
        log.info("Role created successfully with ID: {}", savedRole.getId());
        return roleMapper.toDto(savedRole);
    }

    public RoleDto updateRole(Long id, RoleDto roleDto) {
        log.debug("Attempting to update role with ID: {}", id);
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Role not found for update with ID: {}", id);
                    return new DataNotFoundException(ROLE_NOT_FOUND);
                });
        roleMapper.toUpdateEntity(role, roleDto);
        Role updatedRole = roleRepository.save(role);
        log.info("Role with ID: {} updated successfully", id);
        return roleMapper.toDto(updatedRole);
    }

    public void deleteRole(Long id) {
        log.debug("Attempting to delete role with ID: {}", id);
        roleRepository.deleteById(id);
        log.info("Role with ID: {} deleted successfully", id);
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
        Role role = findRoleById(rolePermissionDto);

        attachPermissionToRole(role, rolePermissionDto.getPermissionIds());
        roleRepository.save(role);
        log.info("Permissions assigned successfully to role ID: {}", rolePermissionDto.getRoleId());
    }

    public void removePermissions(RolePermissionDto rolePermissionDto) {
        log.debug("Attempting to remove permissions from role ID: {}", rolePermissionDto.getRoleId());
        Role role = findRoleById(rolePermissionDto);

        detachPermissionFromRole(role, rolePermissionDto.getPermissionIds());
        roleRepository.save(role);
        log.info("Permissions removed successfully from role ID: {}", rolePermissionDto.getRoleId());
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
