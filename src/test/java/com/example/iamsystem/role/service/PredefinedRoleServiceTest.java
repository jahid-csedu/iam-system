package com.example.iamsystem.role.service;

import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.entity.RoleTemplate;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.role.repository.RoleTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static com.example.iamsystem.role.service.RoleTestData.TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PredefinedRoleServiceTest {

    @Mock
    private RoleTemplateRepository roleTemplateRepository;
    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private PredefinedRoleService service;

    @Test
    @DisplayName("Every template becomes a new role of the tenant with the template's name, description and permissions")
    void createPredefinedRoles_copiesEveryTemplateIntoTheTenant() {
        Permission usersRead = mock(Permission.class);
        Permission usersCreate = mock(Permission.class);
        RoleTemplate admin = template("ADMIN", "Manages the tenant", usersRead, usersCreate);
        RoleTemplate viewer = template("VIEWER", "Views the tenant", usersRead);
        when(roleTemplateRepository.findAll()).thenReturn(List.of(admin, viewer));
        when(roleRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Role> roles = service.createPredefinedRoles(TENANT);

        assertEquals(List.of("ADMIN", "VIEWER"), roles.stream().map(Role::getName).toList());
        Role adminCopy = roles.getFirst();
        assertNull(adminCopy.getId());
        assertSame(TENANT, adminCopy.getTenant());
        assertEquals("Manages the tenant", adminCopy.getDescription());
        assertEquals(Set.of(usersRead, usersCreate), adminCopy.getPermissions());
        assertNotSame(admin.getPermissions(), adminCopy.getPermissions());
    }

    private static RoleTemplate template(String name, String description, Permission... permissions) {
        RoleTemplate template = mock(RoleTemplate.class);
        when(template.getName()).thenReturn(name);
        when(template.getDescription()).thenReturn(description);
        when(template.getPermissions()).thenReturn(Set.of(permissions));
        return template;
    }
}
