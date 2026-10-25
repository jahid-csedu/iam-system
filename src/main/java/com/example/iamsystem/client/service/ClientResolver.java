package com.example.iamsystem.client.service;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.repository.ClientRepository;
import com.example.iamsystem.common.exception.InvalidClientException;
import com.example.iamsystem.common.exception.NoAccessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.example.iamsystem.common.exception.ErrorMessage.INVALID_CLIENT;
import static com.example.iamsystem.common.exception.ErrorMessage.TENANT_SUSPENDED;

/**
 * Resolves the {@code client_id} of a public request (login, token refresh, password reset) to its client and tenant.
 * The clients of a tenant that isn't active are refused, so its users can't log in, refresh or reset passwords.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClientResolver {

    private final ClientRepository clientRepository;

    /**
     * @throws InvalidClientException if no client has this {@code client_id} (401)
     * @throws NoAccessException      if the client's tenant is suspended (403)
     */
    @Transactional(readOnly = true)
    public Client resolve(String clientId) {
        Client client = clientRepository.findByClientId(clientId).orElseThrow(() -> {
            log.warn("Request with unknown client_id '{}'", clientId);
            return new InvalidClientException(INVALID_CLIENT);
        });
        if (!client.getTenant().isActive()) {
            log.warn("Request through client '{}' of suspended tenant {}", clientId, client.getTenant().getId());
            throw new NoAccessException(TENANT_SUSPENDED);
        }
        return client;
    }
}
