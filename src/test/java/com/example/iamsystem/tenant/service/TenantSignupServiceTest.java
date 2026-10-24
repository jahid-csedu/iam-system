package com.example.iamsystem.tenant.service;

import com.example.iamsystem.client.dto.ClientDto;
import com.example.iamsystem.client.entity.ClientType;
import com.example.iamsystem.client.service.ClientService;
import com.example.iamsystem.common.exception.TenantAlreadyExistsException;
import com.example.iamsystem.role.service.PredefinedRoleService;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.tenant.dto.TenantSignupResultDto;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.tenant.mapper.TenantMapper;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.mapper.UserMapper;
import com.example.iamsystem.user.repository.UserRepository;
import com.example.iamsystem.user.service.UserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantSignupServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserFactory userFactory;
    @Mock
    private PredefinedRoleService predefinedRoleService;
    @Mock
    private ClientService clientService;

    private TenantSignupService service;
    private UserCreationDto rootDetails;
    private TenantSignupDto request;

    @BeforeEach
    void setUp() {
        service = new TenantSignupService(tenantRepository, userRepository, userFactory, predefinedRoleService, clientService,
                Mappers.getMapper(TenantMapper.class), Mappers.getMapper(UserMapper.class));
        rootDetails = new UserCreationDto("root", "Password1!", "Root", "root@acme.com", null);
        request = new TenantSignupDto("Acme", rootDetails);
    }

    @Test
    @DisplayName("Signup creates an active tenant, its root user, its predefined roles and its management console")
    void signup_createsActiveTenantAndItsRootUser() {
        when(tenantRepository.existsByName("Acme")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(7L);
            return tenant;
        });
        when(userFactory.newRootUser(any(UserCreationDto.class), any(Tenant.class))).thenAnswer(invocation -> {
            User user = new User();
            user.setUsername("root");
            user.setRootUser(true);
            user.setTenant(invocation.getArgument(1));
            return user;
        });
        ClientDto console = new ClientDto(1L, "console-client-id-000001", "Management Console", ClientType.MANAGEMENT_CONSOLE);
        when(clientService.createManagementConsole(any(Tenant.class))).thenReturn(console);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantSignupResultDto result = service.signup(request);

        Tenant tenant = savedTenant();
        assertEquals("Acme", tenant.getName());
        assertEquals(TenantStatus.ACTIVE, tenant.getStatus());
        assertEquals(7L, result.tenant().id());
        assertTrue(result.rootUser().isRootUser());
        assertEquals(7L, result.rootUser().getTenantId());
        verify(userFactory).newRootUser(rootDetails, tenant);
        verify(predefinedRoleService).createPredefinedRoles(tenant);
        assertEquals(console, result.managementConsole());
    }

    @Test
    @DisplayName("Signup with a taken tenant name creates nothing")
    void signup_whenTenantNameTaken_createsNothing() {
        when(tenantRepository.existsByName("Acme")).thenReturn(true);

        assertThrows(TenantAlreadyExistsException.class, () -> service.signup(request));
        verify(tenantRepository, never()).save(any());
        verifyNoInteractions(userFactory, userRepository, predefinedRoleService, clientService);
    }

    private Tenant savedTenant() {
        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(captor.capture());
        return captor.getValue();
    }
}
