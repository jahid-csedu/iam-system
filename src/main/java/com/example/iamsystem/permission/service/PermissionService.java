package com.example.iamsystem.permission.service;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.permission.dto.PermissionDto;
import com.example.iamsystem.permission.mapper.PermissionMapper;
import com.example.iamsystem.permission.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.iamsystem.common.exception.ErrorMessage.PERMISSION_NOT_FOUND;

/**
 * Reads the fixed permission catalog. Permission checks live in {@code AuthorizationService}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    public List<PermissionDto> getAllPermissions() {
        return permissionMapper.toDtoList(permissionRepository.findAllByOrderByName());
    }

    public PermissionDto getPermissionById(Long id) {
        return permissionRepository.findById(id)
                .map(permissionMapper::toDto)
                .orElseThrow(() -> new DataNotFoundException(PERMISSION_NOT_FOUND));
    }
}
