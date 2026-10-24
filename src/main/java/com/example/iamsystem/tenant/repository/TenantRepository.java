package com.example.iamsystem.tenant.repository;

import com.example.iamsystem.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    boolean existsByName(String name);
}
