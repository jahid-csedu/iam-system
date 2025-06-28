package com.example.iamsystem.role;

import com.example.iamsystem.exception.DataNotFoundException;
import com.example.iamsystem.permission.model.Permission;
import com.example.iamsystem.permission.PermissionRepository;
import com.example.iamsystem.role.model.Role;
import com.example.iamsystem.role.model.RoleDto;
import com.example.iamsystem.role.model.RoleMapper;
import com.example.iamsystem.role.model.RolePermissionDto;
import lombok.RequiredArgsConstructor;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

import static com.example.iamsystem.constant.ErrorMessage.ROLE_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private static final RoleMapper roleMapper = Mappers.getMapper(RoleMapper.class);

    public RoleDto createRole(RoleDto roleDto) {
        Role role = roleMapper.toEntity(roleDto);
        return roleMapper.toDto(roleRepository.save(role));
    }

    public RoleDto updateRole(Long id, RoleDto roleDto) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException(ROLE_NOT_FOUND));
        roleMapper.toUpdateEntity(role, roleDto);
        return roleMapper.toDto(roleRepository.save(role));
    }

    public void deleteRole(Long id) {
        roleRepository.deleteById(id);
    }


    public RoleDto getRole(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException(ROLE_NOT_FOUND));
        return roleMapper.toDto(role);
    }

    public RoleDto getRoleByName(String name) {
        Role role = roleRepository.findByName(name)
                .orElseThrow(() -> new DataNotFoundException(ROLE_NOT_FOUND));
        return roleMapper.toDto(role);
    }

    public List<RoleDto> getRoles() {
        List<Role> roles = roleRepository.findAll();
        return roleMapper.toDto(roles);
    }

    public void assignPermissions(RolePermissionDto rolePermissionDto) {
        Role role = findRoleById(rolePermissionDto);

        attachPermissionToRole(role, rolePermissionDto.getPermissionIds());
        roleRepository.save(role);
    }

    public void removePermissions(RolePermissionDto rolePermissionDto) {
        Role role = findRoleById(rolePermissionDto);

        detachPermissionFromRole(role, rolePermissionDto.getPermissionIds());
        roleRepository.save(role);
    }

    private Role findRoleById(RolePermissionDto rolePermissionDto) {
        return roleRepository.findById(rolePermissionDto.getRoleId())
                .orElseThrow(() -> new DataNotFoundException(ROLE_NOT_FOUND));
    }

    private void attachPermissionToRole(Role role, Set<Long> permissionIds) {
        List<Permission> permissions = getPermissions(permissionIds);

        role.getPermissions().addAll(permissions);
    }

    private void detachPermissionFromRole(Role role, Set<Long> permissionIds) {
        List<Permission> permissions = getPermissions(permissionIds);

        permissions.forEach(role.getPermissions()::remove);
    }

    private List<Permission> getPermissions(Set<Long> permissionIds) {
        List<Permission> permissions = permissionRepository.findAllById(permissionIds);
        if (permissions.size() != permissionIds.size()) {
            throw new DataNotFoundException("Some permissions not found");
        }
        return permissions;
    }
}
