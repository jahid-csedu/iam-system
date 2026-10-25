package com.example.iamsystem.tenant.repository;

import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    boolean existsByName(String name);

    Optional<Tenant> findByType(TenantType type);

    boolean existsByIdAndType(Long id, TenantType type);

    Page<Tenant> findAllByIdIn(Collection<Long> ids, Pageable pageable);
}
