package com.example.iamsystem.client.service;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.repository.ClientRepository;
import com.example.iamsystem.common.exception.InvalidClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.example.iamsystem.common.exception.ErrorMessage.INVALID_CLIENT;

/**
 * Resolves the {@code client_id} of a public request (login, token refresh, password reset) to its client and tenant.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClientResolver {

    private final ClientRepository clientRepository;

    @Transactional(readOnly = true)
    public Client resolve(String clientId) {
        return clientRepository.findByClientId(clientId).orElseThrow(() -> {
            log.warn("Request with unknown client_id '{}'", clientId);
            return new InvalidClientException(INVALID_CLIENT);
        });
    }
}
