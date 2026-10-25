package com.example.iamsystem.security.userdetails;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.tenant.entity.TenantType;
import com.example.iamsystem.tenant.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActingTenantResolverTest {

    private static final Long PLATFORM_ID = 1L;
    private static final AuthenticatedActor PLATFORM_USER = new AuthenticatedActor(7L, "support", PLATFORM_ID, false, 70L);
    private static final AuthenticatedActor TENANT_USER = new AuthenticatedActor(8L, "alice", 100L, true, 80L);

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private ActingTenantResolver resolver;

    @Test
    @DisplayName("Without the header everyone works in their own tenant")
    void noHeader_keepsTheOwnTenant() {
        assertSame(PLATFORM_USER, resolver.resolve(PLATFORM_USER, null));
        verifyNoInteractions(tenantRepository);
    }

    @Test
    @DisplayName("A platform user with the header works in the named tenant, and keeps their home tenant")
    void platformUser_worksInTheNamedTenant() {
        when(tenantRepository.existsByIdAndType(PLATFORM_ID, TenantType.PLATFORM)).thenReturn(true);
        when(tenantRepository.existsById(100L)).thenReturn(true);

        AuthenticatedActor acting = resolver.resolve(PLATFORM_USER, "100");

        assertEquals(100L, acting.tenantId());
        assertEquals(PLATFORM_ID, acting.homeTenantId());
        assertTrue(acting.isActingInOtherTenant());
    }

    @Test
    @DisplayName("A tenant user's header is ignored: they always work in their own tenant")
    void tenantUser_headerIsIgnored() {
        when(tenantRepository.existsByIdAndType(100L, TenantType.PLATFORM)).thenReturn(false);

        AuthenticatedActor result = resolver.resolve(TENANT_USER, "200");

        assertSame(TENANT_USER, result);
        assertFalse(result.isActingInOtherTenant());
    }

    @Test
    @DisplayName("A header that isn't a tenant id is a bad request, and an unknown tenant is not found")
    void badHeader_isRejected() {
        when(tenantRepository.existsByIdAndType(PLATFORM_ID, TenantType.PLATFORM)).thenReturn(true);
        when(tenantRepository.existsById(999L)).thenReturn(false);

        assertThrows(UserInputNotValidException.class, () -> resolver.resolve(PLATFORM_USER, "acme"));
        assertThrows(DataNotFoundException.class, () -> resolver.resolve(PLATFORM_USER, "999"));
    }
}
