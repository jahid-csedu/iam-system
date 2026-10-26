package com.example.iamsystem.user.service;

import com.example.iamsystem.authorization.AuthorizationService;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantType;
import com.example.iamsystem.tenant.mapper.TenantMapper;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.MeDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static com.example.iamsystem.permission.PermissionKey.CLIENTS_READ;
import static com.example.iamsystem.permission.PermissionKey.ROLES_READ;
import static com.example.iamsystem.permission.PermissionKey.USERS_READ;
import static com.example.iamsystem.user.service.UserTestData.TENANT;
import static com.example.iamsystem.user.service.UserTestData.actor;
import static com.example.iamsystem.user.service.UserTestData.tenant;
import static com.example.iamsystem.user.service.UserTestData.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeServiceTest {

    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private AuthorizationService authorizationService;

    private MeService service;
    private User alice;

    @BeforeEach
    void setUp() {
        service = new MeService(currentUserProvider, userRepository, tenantRepository, authorizationService,
                Mappers.getMapper(TenantMapper.class));
        alice = user(2, "alice");
        alice.setFullName("Alice Admin");
        alice.setUserType(UserType.MANAGEMENT);
    }

    @Test
    @DisplayName("A management user gets their profile, their tenant as home and working tenant, and their sorted permission keys")
    void currentUser_ofAManagementUser_describesThem() {
        AuthenticatedActor actor = actor(alice);
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor);
        when(userRepository.getRequiredByIdAndTenantId(2L, TENANT.getId())).thenReturn(alice);
        when(authorizationService.permissionsOf(actor)).thenReturn(EnumSet.of(USERS_READ, ROLES_READ, CLIENTS_READ));

        MeDto me = service.currentUser();

        assertEquals(2L, me.id());
        assertEquals("alice", me.username());
        assertEquals("Alice Admin", me.fullName());
        assertEquals("alice@example.com", me.email());
        assertEquals(UserType.MANAGEMENT, me.userType());
        assertFalse(me.rootUser());
        assertEquals(TENANT.getId(), me.homeTenant().id());
        assertSame(me.homeTenant(), me.workingTenant());
        assertEquals(List.of("clients:read", "roles:read", "users:read"), me.permissions());
        verifyNoInteractions(tenantRepository);
    }

    @Test
    @DisplayName("A platform user working inside another tenant gets that tenant as working tenant and their permissions there")
    void currentUser_insideAnotherTenant_describesThatTenant() {
        Tenant platform = tenant(1L, "Platform");
        platform.setType(TenantType.PLATFORM);
        alice.setTenant(platform);
        AuthenticatedActor acting = actor(alice).actingIn(TENANT.getId());
        when(currentUserProvider.requireCurrentActor()).thenReturn(acting);
        when(userRepository.getRequiredByIdAndTenantId(2L, 1L)).thenReturn(alice);
        when(tenantRepository.findById(TENANT.getId())).thenReturn(Optional.of(TENANT));
        when(authorizationService.permissionsOf(acting)).thenReturn(EnumSet.of(USERS_READ));

        MeDto me = service.currentUser();

        assertEquals(1L, me.homeTenant().id());
        assertEquals(TenantType.PLATFORM, me.homeTenant().type());
        assertEquals(TENANT.getId(), me.workingTenant().id());
        assertEquals(List.of("users:read"), me.permissions());
    }

    @Test
    @DisplayName("A regular user is refused")
    void currentUser_ofARegularUser_isRefused() {
        User bob = user(3, "bob");
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor(bob));
        when(userRepository.getRequiredByIdAndTenantId(3L, TENANT.getId())).thenReturn(bob);

        NoAccessException refused = assertThrows(NoAccessException.class, () -> service.currentUser());

        assertEquals("Only management users can use this endpoint", refused.getMessage());
        verify(authorizationService, never()).permissionsOf(any());
    }
}
