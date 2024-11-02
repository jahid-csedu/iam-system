package com.example.management.permission;

import com.example.management.exception.DataNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mapstruct.factory.Mappers;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor()
public class PermissionService {
    private final PermissionRepository permissionRepository;
    private static final PermissionMapper permissionMapper = Mappers.getMapper(PermissionMapper.class);
    private static final String PERMISSION_NOT_FOUND = "Permission not found";

    @PreAuthorize("hasAuthority('WRITE_PRIVILEGES')")
    public PermissionDto savePermission(PermissionDto permissionDto) {
        Permission permission = permissionRepository.save(permissionMapper.toEntity(permissionDto));
        return permissionMapper.toDto(permission);
    }

    public PermissionDto getPermissionById(Long id) {
        return permissionRepository.findById(id)
                .map(permissionMapper::toDto)
                .orElseThrow(() -> new DataNotFoundException(PERMISSION_NOT_FOUND));
    }

    @PreAuthorize("hasAuthority('WRITE_PRIVILEGES')")
    public PermissionDto updatePermission(Long id, PermissionDto permissionDto) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException(PERMISSION_NOT_FOUND));
        permissionMapper.toUpdateEntity(permission, permissionDto);
        return permissionMapper.toDto(permissionRepository.save(permission));
    }

    @PreAuthorize("hasAuthority('ADMIN_PRIVILEGES')")
    public void deletePermissionById(Long id) {
        permissionRepository.deleteById(id);
    }

    public PermissionDto getPermissionByName(String name) {
        return permissionRepository.findByName(name)
                .map(permissionMapper::toDto)
                .orElseThrow(() -> new DataNotFoundException(PERMISSION_NOT_FOUND));
    }

    public List<PermissionDto> getAllPermissions() {
        return permissionMapper.toDto(permissionRepository.findAll());
    }
}
