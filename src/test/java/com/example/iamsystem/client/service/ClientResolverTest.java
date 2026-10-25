package com.example.iamsystem.client.service;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.repository.ClientRepository;
import com.example.iamsystem.common.exception.InvalidClientException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientResolverTest {

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private ClientResolver resolver;

    @Test
    @DisplayName("A client of an active tenant is resolved")
    void clientOfActiveTenant_isResolved() {
        Client client = clientOf(TenantStatus.ACTIVE);
        when(clientRepository.findByClientId("shop")).thenReturn(Optional.of(client));

        assertSame(client, resolver.resolve("shop"));
    }

    @Test
    @DisplayName("A client of a suspended tenant is refused")
    void clientOfSuspendedTenant_isRefused() {
        when(clientRepository.findByClientId("shop")).thenReturn(Optional.of(clientOf(TenantStatus.SUSPENDED)));

        assertThrows(NoAccessException.class, () -> resolver.resolve("shop"));
    }

    @Test
    @DisplayName("An unknown client_id is an invalid client")
    void unknownClient_isInvalid() {
        when(clientRepository.findByClientId("nope")).thenReturn(Optional.empty());

        assertThrows(InvalidClientException.class, () -> resolver.resolve("nope"));
    }

    private static Client clientOf(TenantStatus status) {
        Tenant tenant = new Tenant("Acme", status);
        tenant.setId(100L);
        Client client = new Client();
        client.setTenant(tenant);
        return client;
    }
}
