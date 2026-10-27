package com.example.iamsystem.client.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.client.dto.ClientDto;
import com.example.iamsystem.client.dto.ClientRequestDto;
import com.example.iamsystem.client.dto.TokenSettingsDto;
import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.entity.ClientStatus;
import com.example.iamsystem.client.entity.ClientType;
import com.example.iamsystem.client.entity.TokenClaim;
import com.example.iamsystem.client.mapper.ClientMapper;
import com.example.iamsystem.client.repository.ClientRepository;
import com.example.iamsystem.common.exception.AlreadyExistsException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static com.example.iamsystem.common.exception.ErrorMessage.CLIENT_EXISTS;
import static com.example.iamsystem.common.exception.ErrorMessage.CONSOLE_CLIENT_NOT_DELETABLE;
import static com.example.iamsystem.common.exception.ErrorMessage.CONSOLE_CLIENT_NOT_SUSPENDABLE;
import static com.example.iamsystem.common.exception.ErrorMessage.PLATFORM_CONSOLE_ONLY;

/**
 * Manages the client applications of the current user's tenant, and creates each new tenant's management console.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {

    public static final String MANAGEMENT_CONSOLE_NAME = "Management Console";

    /**
     * The token settings of every new client: 5-minute access tokens, refresh tokens for 24 hours, the username claim.
     */
    public static final TokenSettingsDto DEFAULT_TOKEN_SETTINGS =
            new TokenSettingsDto(5 * 60, true, 24 * 60 * 60, Set.of(TokenClaim.USERNAME));

    private final ClientRepository clientRepository;
    private final TenantRepository tenantRepository;
    private final ClientIdGenerator clientIdGenerator;
    private final CurrentUserProvider currentUserProvider;
    private final ClientMapper clientMapper;

    /**
     * Runs inside the signup transaction, so a failed signup leaves no client behind.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ClientDto createManagementConsole(Tenant tenant) {
        return clientMapper.toDto(clientRepository.save(newClient(tenant, MANAGEMENT_CONSOLE_NAME, ClientType.MANAGEMENT_CONSOLE)));
    }

    @Auditable(
            value = AuditEventType.CLIENT_CREATION,
            target = "#request.name()",
            detailsExpression = "T(java.util.Map).of('client_id', #result.clientId())"
    )
    @Transactional
    public ClientDto createClient(ClientRequestDto request) {
        Long tenantId = currentUserProvider.requireCurrentTenantId();
        Tenant tenant = tenantRepository.getReferenceById(tenantId);
        if (tenant.isPlatform()) {
            throw new UserInputNotValidException(PLATFORM_CONSOLE_ONLY);
        }
        if (clientRepository.existsByNameAndTenantId(request.name(), tenantId)) {
            throw new AlreadyExistsException(CLIENT_EXISTS);
        }
        Client client = newClient(tenant, request.name(), ClientType.APPLICATION);
        return clientMapper.toDto(clientRepository.save(client));
    }

    @Auditable(
            value = AuditEventType.CLIENT_UPDATE,
            target = "#id"
    )
    @Transactional
    public ClientDto renameClient(Long id, ClientRequestDto request) {
        Long tenantId = currentUserProvider.requireCurrentTenantId();
        Client client = clientRepository.getRequiredByIdAndTenantId(id, tenantId);
        if (clientRepository.existsByNameAndTenantIdAndIdNot(request.name(), tenantId, id)) {
            throw new AlreadyExistsException(CLIENT_EXISTS);
        }
        client.setName(request.name());
        return clientMapper.toDto(clientRepository.save(client));
    }

    /**
     * Applies to tokens issued from now on; tokens already issued keep their lifetime and claims.
     */
    @Auditable(
            value = AuditEventType.CLIENT_TOKEN_SETTINGS_UPDATE,
            target = "#id",
            detailsExpression = "T(java.util.Map).of('settings', #request)"
    )
    @Transactional
    public ClientDto updateTokenSettings(Long id, TokenSettingsDto request) {
        Client client = clientRepository.getRequiredByIdAndTenantId(id, currentUserProvider.requireCurrentTenantId());
        applyTokenSettings(client, request);
        return clientMapper.toDto(clientRepository.save(client));
    }

    /**
     * The management console can't be deleted: it is how the tenant's management users log in.
     */
    @Auditable(
            value = AuditEventType.CLIENT_DELETE,
            target = "#id"
    )
    @Transactional
    public void deleteClient(Long id) {
        Client client = clientRepository.getRequiredByIdAndTenantId(id, currentUserProvider.requireCurrentTenantId());
        if (client.isManagementConsole()) {
            throw new UserInputNotValidException(CONSOLE_CLIENT_NOT_DELETABLE);
        }
        clientRepository.delete(client);
    }

    /**
     * Nobody can log in, refresh or reset passwords through the client, and the tokens of its sessions stop working until
     * it is activated again; sessions are kept. The management console is never suspended. Suspending a suspended client
     * changes nothing.
     */
    @Auditable(value = AuditEventType.CLIENT_SUSPEND, target = "#id")
    @Transactional
    public ClientDto suspendClient(Long id) {
        Client client = clientRepository.getRequiredByIdAndTenantId(id, currentUserProvider.requireCurrentTenantId());
        if (client.isManagementConsole()) {
            throw new UserInputNotValidException(CONSOLE_CLIENT_NOT_SUSPENDABLE);
        }
        return changeStatus(client, ClientStatus.SUSPENDED);
    }

    /**
     * Logins work again, and so do the tokens of unexpired sessions. Activating an active client changes nothing.
     */
    @Auditable(value = AuditEventType.CLIENT_ACTIVATE, target = "#id")
    @Transactional
    public ClientDto activateClient(Long id) {
        return changeStatus(clientRepository.getRequiredByIdAndTenantId(id, currentUserProvider.requireCurrentTenantId()),
                ClientStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<ClientDto> getClients() {
        return clientMapper.toDtoList(clientRepository.findAllByTenantIdOrderByName(currentUserProvider.requireCurrentTenantId()));
    }

    @Transactional(readOnly = true)
    public ClientDto getClient(Long id) {
        return clientMapper.toDto(clientRepository.getRequiredByIdAndTenantId(id, currentUserProvider.requireCurrentTenantId()));
    }

    private Client newClient(Tenant tenant, String name, ClientType type) {
        Client client = new Client();
        client.setTenant(tenant);
        client.setClientId(clientIdGenerator.newClientId());
        client.setName(name);
        client.setType(type);
        applyTokenSettings(client, DEFAULT_TOKEN_SETTINGS);
        return client;
    }

    private static void applyTokenSettings(Client client, TokenSettingsDto settings) {
        client.setAccessTokenTtlSeconds(settings.accessTokenTtlSeconds());
        client.setRefreshTokenEnabled(settings.refreshTokenEnabled());
        client.setRefreshTokenTtlSeconds(settings.refreshTokenTtlSeconds());
        client.getTokenClaims().clear();
        client.getTokenClaims().addAll(settings.claims());
    }

    private ClientDto changeStatus(Client client, ClientStatus status) {
        if (client.getStatus() != status) {
            client.setStatus(status);
            log.info("Client '{}' is now {}", client.getClientId(), status);
        }
        return clientMapper.toDto(client);
    }
}
