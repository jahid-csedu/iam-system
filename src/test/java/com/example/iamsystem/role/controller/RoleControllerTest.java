package com.example.iamsystem.role.controller;

import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.repository.PermissionRepository;
import com.example.iamsystem.role.dto.RolePermissionDto;
import com.example.iamsystem.role.dto.RoleRequestDto;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import com.example.iamsystem.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static com.example.iamsystem.security.userdetails.TestActors.actingAs;
import static com.example.iamsystem.user.repository.TestUserLookup.findByUsername;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One tenant with an ADMIN (every roles:* permission) and a READ_ONLY user, each holding their own role.
 * The persistence context is cleared after setup, so every request starts from the database, as in production.
 * Cross-tenant behaviour is covered by {@code TenantIsolationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private EntityManager entityManager;

    private Tenant tenant;
    private User admin;
    private User readOnlyUser;
    private Role adminRole;
    private Role readOnlyRole;
    private Permission readPermission;
    private Permission writePermission;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.save(new Tenant("Role Test Tenant", TenantStatus.ACTIVE));
        readPermission = permission("roles:read");
        writePermission = permission("roles:create");
        adminRole = role("ADMIN", readPermission, writePermission, permission("roles:update"), permission("roles:delete"));
        readOnlyRole = role("READ_ONLY", readPermission);
        admin = user("admin", adminRole);
        readOnlyUser = user("reader", readOnlyRole);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Lists the tenant's roles ordered by name")
    void getAllRoles_returnsTheTenantsRolesByName() throws Exception {
        perform(get("/api/roles"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("ADMIN", "READ_ONLY")));
    }

    @Test
    @DisplayName("Listing roles without authentication is unauthorized")
    void getAllRoles_withoutAuthentication_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/roles")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A role is created in the caller's tenant")
    void createRole_isCreatedInTheCallersTenant() throws Exception {
        perform(post("/api/roles"), admin, new RoleRequestDto("AUDITOR", "Reads audit data"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("AUDITOR")))
                .andExpect(jsonPath("$.permissionIds").isEmpty());

        Role created = roleRepository.findByNameAndTenantId("AUDITOR", tenant.getId()).orElseThrow();
        assertEquals("Reads audit data", created.getDescription());
    }

    @Test
    @DisplayName("A role name already used in the tenant is a conflict")
    void createRole_withNameTakenInTheTenant_isConflict() throws Exception {
        perform(post("/api/roles"), admin, new RoleRequestDto("READ_ONLY", null))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("A role without a name fails validation: a 422 problem that names the field")
    void createRole_withoutName_failsValidation() throws Exception {
        perform(post("/api/roles"), admin, new RoleRequestDto(" ", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Some fields are invalid"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("Role name cannot be null or blank"));
    }

    @Test
    @DisplayName("Creating a role without roles:create is forbidden")
    void createRole_withoutWritePermission_isForbidden() throws Exception {
        perform(post("/api/roles"), readOnlyUser, new RoleRequestDto("AUDITOR", null))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A role is found by id with its permissions")
    void getRoleById_returnsRole() throws Exception {
        perform(get("/api/roles/{id}", adminRole.getId()), readOnlyUser)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("ADMIN")))
                .andExpect(jsonPath("$.permissionIds.length()", is(4)));
    }

    @Test
    @DisplayName("An unknown role id is not found")
    void getRoleById_unknownId_isNotFound() throws Exception {
        perform(get("/api/roles/{id}", -1), admin).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A role is found by name")
    void getRoleByName_returnsRole() throws Exception {
        perform(get("/api/roles/name/{name}", "READ_ONLY"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(readOnlyRole.getId().intValue())));
    }

    @Test
    @DisplayName("A role the caller doesn't hold is updated")
    void updateRole_aRoleTheCallerDoesNotHold_isUpdated() throws Exception {
        perform(put("/api/roles/{id}", readOnlyRole.getId()), admin, new RoleRequestDto("VIEWER", "Updated"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("VIEWER")))
                .andExpect(jsonPath("$.description", is("Updated")));
    }

    @Test
    @DisplayName("Updating a role the caller holds is forbidden")
    void updateRole_aRoleTheCallerHolds_isForbidden() throws Exception {
        perform(put("/api/roles/{id}", adminRole.getId()), admin, new RoleRequestDto("SUPER_ADMIN", null))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Renaming a role to another role's name is a conflict")
    void updateRole_toTheNameOfAnotherRole_isConflict() throws Exception {
        perform(put("/api/roles/{id}", readOnlyRole.getId()), admin, new RoleRequestDto("ADMIN", null))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Updating a role without roles:update is forbidden")
    void updateRole_withoutUpdatePermission_isForbidden() throws Exception {
        perform(put("/api/roles/{id}", adminRole.getId()), readOnlyUser, new RoleRequestDto("ADMIN", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deleting a role removes it from its users")
    void deleteRole_removesItFromItsUsers() throws Exception {
        perform(delete("/api/roles/{id}", readOnlyRole.getId()), admin).andExpect(status().isNoContent());

        assertFalse(roleRepository.existsById(readOnlyRole.getId()));
        assertEquals(Set.of(), findByUsername(userRepository, "reader").orElseThrow().getRoles());
    }

    @Test
    @DisplayName("Deleting a role the caller holds is forbidden")
    void deleteRole_aRoleTheCallerHolds_isForbidden() throws Exception {
        perform(delete("/api/roles/{id}", adminRole.getId()), admin).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deleting a role without roles:delete is forbidden")
    void deleteRole_withoutDeletePermission_isForbidden() throws Exception {
        perform(delete("/api/roles/{id}", adminRole.getId()), readOnlyUser).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Assigned permissions are added to the role")
    void assignPermissions_addsThemToTheRole() throws Exception {
        perform(put("/api/roles/permissions"), admin, new RolePermissionDto(readOnlyRole.getId(), Set.of(writePermission.getId())))
                .andExpect(status().isNoContent());

        assertEquals(Set.of(readPermission, writePermission), roleRepository.findById(readOnlyRole.getId()).orElseThrow().getPermissions());
    }

    @Test
    @DisplayName("Assigning permissions to a role the caller holds is forbidden")
    void assignPermissions_toARoleTheCallerHolds_isForbidden() throws Exception {
        perform(put("/api/roles/permissions"), admin, new RolePermissionDto(adminRole.getId(), Set.of(readPermission.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Assigning an unknown permission is not found")
    void assignPermissions_unknownPermission_isNotFound() throws Exception {
        perform(put("/api/roles/permissions"), admin, new RolePermissionDto(readOnlyRole.getId(), Set.of(-1L)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Assigning permissions without roles:update is forbidden")
    void assignPermissions_withoutUpdatePermission_isForbidden() throws Exception {
        perform(put("/api/roles/permissions"), readOnlyUser, new RolePermissionDto(adminRole.getId(), Set.of(readPermission.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Removing permissions removes only the given ones")
    void removePermissions_removesOnlyTheGivenOnes() throws Exception {
        perform(put("/api/roles/permissions"), admin, new RolePermissionDto(readOnlyRole.getId(), Set.of(writePermission.getId())))
                .andExpect(status().isNoContent());

        perform(delete("/api/roles/permissions"), admin, new RolePermissionDto(readOnlyRole.getId(), Set.of(readPermission.getId())))
                .andExpect(status().isNoContent());

        assertEquals(Set.of(writePermission), roleRepository.findById(readOnlyRole.getId()).orElseThrow().getPermissions());
    }

    @Test
    @DisplayName("Removing permissions without roles:update is forbidden")
    void removePermissions_withoutUpdatePermission_isForbidden() throws Exception {
        perform(delete("/api/roles/permissions"), readOnlyUser, new RolePermissionDto(adminRole.getId(), Set.of(readPermission.getId())))
                .andExpect(status().isForbidden());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, User actor) throws Exception {
        return mockMvc.perform(request.with(actingAs(actor)));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, User actor, Object body) throws Exception {
        return perform(request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body)), actor);
    }

    private Permission permission(String name) {
        return permissionRepository.findAllByOrderByName().stream()
                .filter(permission -> permission.getName().equals(name))
                .findFirst().orElseThrow();
    }

    private Role role(String name, Permission... permissions) {
        Role role = new Role();
        role.setName(name);
        role.setTenant(tenant);
        role.getPermissions().addAll(Set.of(permissions));
        return roleRepository.save(role);
    }

    private User user(String username, Role role) {
        User user = new User();
        user.setTenant(tenant);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setActive(true);
        user.setUserType(UserType.MANAGEMENT);
        user.getRoles().add(role);
        return userRepository.save(user);
    }
}
