package com.example.iamsystem.permission.repository;

import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.entity.PermissionScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByName();

    List<Permission> findAllByIdInAndScope(Collection<Long> ids, PermissionScope scope);

    /**
     * Whether any role of the user grants the permission named {@code name}.
     */
    @Query("""
            SELECT COUNT(p) > 0
            FROM User u JOIN u.roles r JOIN r.permissions p
            WHERE u.id = :userId AND p.name = :name
            """)
    boolean isGrantedToUser(@Param("userId") Long userId, @Param("name") String name);
}
