package com.example.iamsystem.permission.repository;

import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.entity.PermissionAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    List<Permission> findAllByServiceName(String name);
    Optional<Permission> findByServiceNameAndAction(String serviceName, PermissionAction action);
}
