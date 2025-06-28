package com.example.iamsystem.permission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    List<Permission> findAllByServiceName(String name);
    Optional<Permission> findByServiceNameAndAction(String serviceName, PermissionAction action);
}
