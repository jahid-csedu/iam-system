package com.example.iamsystem.permission.controller;

import com.example.iamsystem.permission.PermissionKey;
import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.repository.PermissionRepository;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static com.example.iamsystem.permission.PermissionKey.PERMISSIONS_READ;
import static com.example.iamsystem.security.userdetails.TestActors.actingAs;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PermissionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TenantRepository tenantRepository;

    private User root;
    private User reader;
    private User outsider;
    private Permission usersRead;

    @BeforeEach
    void setUp() {
        Tenant tenant = tenantRepository.save(new Tenant("Permission Test Tenant", TenantStatus.ACTIVE));
        usersRead = catalogEntry("users:read");

        Role catalogReader = new Role();
        catalogReader.setTenant(tenant);
        catalogReader.setName("CATALOG_READER");
        catalogReader.getPermissions().add(catalogEntry(PERMISSIONS_READ.key()));
        roleRepository.save(catalogReader);

        root = user(tenant, "root", true);
        reader = user(tenant, "reader", false);
        reader.getRoles().add(catalogReader);
        userRepository.save(reader);
        outsider = user(tenant, "outsider", false);
    }

    @Test
    @DisplayName("The PermissionKey enum and the migrated catalog contain the same keys and scopes")
    void catalog_matchesPermissionKeys() {
        Map<String, String> enumKeys = Arrays.stream(PermissionKey.values())
                .collect(Collectors.toMap(PermissionKey::key, key -> key.scope().name()));
        Map<String, String> catalog = permissionRepository.findAll().stream()
                .collect(Collectors.toMap(Permission::getName, permission -> permission.getScope().name()));

        assertEquals(enumKeys, catalog);
    }

    @Test
    @DisplayName("A user with permissions:read lists the catalog ordered by name")
    void getAllPermissions_withPermission_listsTheCatalog() throws Exception {
        mockMvc.perform(get("/api/permissions").with(actingAs(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(PermissionKey.values().length)))
                .andExpect(jsonPath("$[0].name", is("clients:activate")))
                .andExpect(jsonPath("$[0].scope", is("TENANT")));
    }

    @Test
    @DisplayName("The root user lists the catalog without any role")
    void getAllPermissions_asRoot_isAllowed() throws Exception {
        mockMvc.perform(get("/api/permissions").with(actingAs(root))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A user without permissions:read can neither list nor get permissions")
    void catalog_withoutPermission_isForbidden() throws Exception {
        mockMvc.perform(get("/api/permissions").with(actingAs(outsider))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/permissions/{id}", usersRead.getId()).with(actingAs(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A permission is found by id")
    void getPermissionById_returnsPermission() throws Exception {
        mockMvc.perform(get("/api/permissions/{id}", usersRead.getId()).with(actingAs(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("users:read")))
                .andExpect(jsonPath("$.description").isNotEmpty());
    }

    @Test
    @DisplayName("An unknown permission id is not found")
    void getPermissionById_unknownId_isNotFound() throws Exception {
        mockMvc.perform(get("/api/permissions/{id}", -1).with(actingAs(root))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Permissions cannot be created or deleted through the API")
    void catalog_isReadOnly() throws Exception {
        mockMvc.perform(post("/api/permissions").with(actingAs(root))).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/permissions/{id}", usersRead.getId()).with(actingAs(root)))
                .andExpect(status().isMethodNotAllowed());
    }

    private Permission catalogEntry(String name) {
        return permissionRepository.findAllByOrderByName().stream()
                .filter(permission -> permission.getName().equals(name))
                .findFirst().orElseThrow();
    }

    private User user(Tenant tenant, String username, boolean rootUser) {
        User user = new User();
        user.setTenant(tenant);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setActive(true);
        user.setUserType(UserType.MANAGEMENT);
        user.setRootUser(rootUser);
        return userRepository.save(user);
    }
}
