package com.example.iamsystem.permission.repository;

import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.entity.PermissionAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByServiceName(String serviceName);

    Optional<Permission> findByServiceNameAndAction(String serviceName, PermissionAction action);

    /**
     * Whether any role of the user grants {@code serviceName:action}.
     */
    @Query("""
            SELECT COUNT(p) > 0
            FROM User u JOIN u.roles r JOIN r.permissions p
            WHERE u.id = :userId AND p.serviceName = :serviceName AND p.action = :action
            """)
    boolean isGrantedToUser(@Param("userId") Long userId,
                            @Param("serviceName") String serviceName,
                            @Param("action") PermissionAction action);
}
