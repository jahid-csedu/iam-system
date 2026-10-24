package com.example.iamsystem.client.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.client.dto.ClientDto;
import com.example.iamsystem.client.dto.ClientRequestDto;
import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.entity.ClientType;
import com.example.iamsystem.client.mapper.ClientMapper;
import com.example.iamsystem.client.repository.ClientRepository;
import com.example.iamsystem.common.exception.ClientAlreadyExistsException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.iamsystem.common.exception.ErrorMessage.CLIENT_EXISTS;
import static com.example.iamsystem.common.exception.ErrorMessage.CONSOLE_CLIENT_NOT_DELETABLE;

/**
 * Manages the client applications of the current user's tenant, and creates each new tenant's management console.
 */
@Service
@RequiredArgsConstructor
public class ClientService {

    public static final String MANAGEMENT_CONSOLE_NAME = "Management Console";

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
        if (clientRepository.existsByNameAndTenantId(request.name(), tenantId)) {
            throw new ClientAlreadyExistsException(CLIENT_EXISTS);
        }
        Client client = newClient(tenantRepository.getReferenceById(tenantId), request.name(), ClientType.APPLICATION);
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
            throw new ClientAlreadyExistsException(CLIENT_EXISTS);
        }
        client.setName(request.name());
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
        return client;
    }
}
