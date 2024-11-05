package com.example.iamsystem.permission;

import com.example.iamsystem.exception.DataNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.example.iamsystem.constant.ErrorMessage.PERMISSION_NOT_FOUND;

@Service
@RequiredArgsConstructor()
public class PermissionService {
    private final PermissionRepository permissionRepository;
    private static final PermissionMapper permissionMapper = Mappers.getMapper(PermissionMapper.class);

    public PermissionDto savePermission(PermissionDto permissionDto) {
        Permission entity = permissionMapper.toEntity(permissionDto);
        Permission permission = permissionRepository.save(entity);
        return permissionMapper.toDto(permission);
    }

    public PermissionDto getPermissionById(Long id) {
        return permissionRepository.findById(id)
                .map(permissionMapper::toDto)
                .orElseThrow(() -> new DataNotFoundException(PERMISSION_NOT_FOUND));
    }

    public PermissionDto updatePermission(Long id, PermissionDto permissionDto) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException(PERMISSION_NOT_FOUND));
        permissionMapper.toUpdateEntity(permission, permissionDto);
        return permissionMapper.toDto(permissionRepository.save(permission));
    }

    public void deletePermissionById(Long id) {
        permissionRepository.deleteById(id);
    }

    public PermissionDto getPermissionByServiceName(String name) {
        return permissionRepository.findByServiceName(name)
                .map(permissionMapper::toDto)
                .orElseThrow(() -> new DataNotFoundException(PERMISSION_NOT_FOUND));
    }

    public List<PermissionDto> getAllPermissions() {
        return permissionMapper.toDto(permissionRepository.findAll());
    }
}
