package com.example.iamsystem.tenant.service;

import com.example.iamsystem.common.exception.TenantAlreadyExistsException;
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

    private TenantSignupService service;
    private UserCreationDto rootDetails;
    private TenantSignupDto request;

    @BeforeEach
    void setUp() {
        service = new TenantSignupService(tenantRepository, userRepository, userFactory,
                Mappers.getMapper(TenantMapper.class), Mappers.getMapper(UserMapper.class));
        rootDetails = new UserCreationDto("root", "Password1!", "Root", "root@acme.com");
        request = new TenantSignupDto("Acme", rootDetails);
    }

    @Test
    void signup_createsActiveTenantAndItsRootUser() {
        when(tenantRepository.existsByName("Acme")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(7L);
            return tenant;
        });
        when(userFactory.newUser(any(UserCreationDto.class), any(Tenant.class))).thenAnswer(invocation -> {
            User user = new User();
            user.setUsername("root");
            user.setTenant(invocation.getArgument(1));
            return user;
        });
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantSignupResultDto result = service.signup(request);

        Tenant tenant = savedTenant();
        assertEquals("Acme", tenant.getName());
        assertEquals(TenantStatus.ACTIVE, tenant.getStatus());
        assertEquals(7L, result.tenant().id());
        assertTrue(result.rootUser().isRootUser());
        assertEquals(7L, result.rootUser().getTenantId());
        verify(userFactory).newUser(rootDetails, tenant);
    }

    @Test
    void signup_whenTenantNameTaken_createsNothing() {
        when(tenantRepository.existsByName("Acme")).thenReturn(true);

        assertThrows(TenantAlreadyExistsException.class, () -> service.signup(request));
        verify(tenantRepository, never()).save(any());
        verifyNoInteractions(userFactory, userRepository);
    }

    private Tenant savedTenant() {
        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(captor.capture());
        return captor.getValue();
    }
}
