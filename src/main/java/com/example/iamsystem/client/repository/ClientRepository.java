package com.example.iamsystem.client.repository;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.common.exception.DataNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import static com.example.iamsystem.common.exception.ErrorMessage.CLIENT_NOT_FOUND;

/**
 * Management lookups are tenant-scoped; {@link #findByClientId} serves the public login and password-reset endpoints.
 */
public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByClientId(String clientId);

    List<Client> findAllByTenantIdOrderByName(Long tenantId);

    Optional<Client> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByNameAndTenantId(String name, Long tenantId);

    boolean existsByNameAndTenantIdAndIdNot(String name, Long tenantId, Long id);

    default Client getRequiredByIdAndTenantId(Long id, Long tenantId) {
        return findByIdAndTenantId(id, tenantId).orElseThrow(() -> new DataNotFoundException(CLIENT_NOT_FOUND));
    }
}
