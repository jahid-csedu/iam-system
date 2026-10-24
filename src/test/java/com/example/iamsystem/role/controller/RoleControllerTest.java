package com.example.iamsystem.role.controller;

import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.entity.PermissionAction;
import com.example.iamsystem.permission.repository.PermissionRepository;
import com.example.iamsystem.role.dto.RolePermissionDto;
import com.example.iamsystem.role.dto.RoleRequestDto;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.security.userdetails.DefaultUserDetails;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static com.example.iamsystem.permission.PermissionConstants.IAM_SERVICE_NAME;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One tenant with an ADMIN (all IAM permissions) and a READ_ONLY user, each holding their own role.
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
        readPermission = permission(PermissionAction.READ);
        writePermission = permission(PermissionAction.WRITE);
        adminRole = role("ADMIN", readPermission, writePermission, permission(PermissionAction.UPDATE),
                permission(PermissionAction.DELETE));
        readOnlyRole = role("READ_ONLY", readPermission);
        admin = user("admin", adminRole);
        readOnlyUser = user("reader", readOnlyRole);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void getAllRoles_returnsTheTenantsRolesByName() throws Exception {
        perform(get("/api/roles"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("ADMIN", "READ_ONLY")));
    }

    @Test
    void getAllRoles_withoutAuthentication_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/roles")).andExpect(status().isUnauthorized());
    }

    @Test
    void createRole_isCreatedInTheCallersTenant() throws Exception {
        perform(post("/api/roles"), admin, new RoleRequestDto("AUDITOR", "Reads audit data"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("AUDITOR")))
                .andExpect(jsonPath("$.permissionIds").isEmpty());

        Role created = roleRepository.findByNameAndTenantId("AUDITOR", tenant.getId()).orElseThrow();
        assertEquals("Reads audit data", created.getDescription());
    }

    @Test
    void createRole_withNameTakenInTheTenant_isBadRequest() throws Exception {
        perform(post("/api/roles"), admin, new RoleRequestDto("READ_ONLY", null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRole_withoutName_failsValidation() throws Exception {
        perform(post("/api/roles"), admin, new RoleRequestDto(" ", null))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createRole_withoutWritePermission_isForbidden() throws Exception {
        perform(post("/api/roles"), readOnlyUser, new RoleRequestDto("AUDITOR", null))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRoleById_returnsRole() throws Exception {
        perform(get("/api/roles/{id}", adminRole.getId()), readOnlyUser)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("ADMIN")))
                .andExpect(jsonPath("$.permissionIds.length()", is(4)));
    }

    @Test
    void getRoleById_unknownId_isNotFound() throws Exception {
        perform(get("/api/roles/{id}", -1), admin).andExpect(status().isNotFound());
    }

    @Test
    void getRoleByName_returnsRole() throws Exception {
        perform(get("/api/roles/name/{name}", "READ_ONLY"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(readOnlyRole.getId().intValue())));
    }

    @Test
    void updateRole_aRoleTheCallerDoesNotHold_isUpdated() throws Exception {
        perform(put("/api/roles/{id}", readOnlyRole.getId()), admin, new RoleRequestDto("VIEWER", "Updated"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("VIEWER")))
                .andExpect(jsonPath("$.description", is("Updated")));
    }

    @Test
    void updateRole_aRoleTheCallerHolds_isForbidden() throws Exception {
        perform(put("/api/roles/{id}", adminRole.getId()), admin, new RoleRequestDto("SUPER_ADMIN", null))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateRole_toTheNameOfAnotherRole_isBadRequest() throws Exception {
        perform(put("/api/roles/{id}", readOnlyRole.getId()), admin, new RoleRequestDto("ADMIN", null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRole_withoutUpdatePermission_isForbidden() throws Exception {
        perform(put("/api/roles/{id}", adminRole.getId()), readOnlyUser, new RoleRequestDto("ADMIN", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteRole_removesItFromItsUsers() throws Exception {
        perform(delete("/api/roles/{id}", readOnlyRole.getId()), admin).andExpect(status().isNoContent());

        assertFalse(roleRepository.existsById(readOnlyRole.getId()));
        assertEquals(Set.of(), userRepository.findByUsername("reader").orElseThrow().getRoles());
    }

    @Test
    void deleteRole_aRoleTheCallerHolds_isForbidden() throws Exception {
        perform(delete("/api/roles/{id}", adminRole.getId()), admin).andExpect(status().isForbidden());
    }

    @Test
    void deleteRole_withoutDeletePermission_isForbidden() throws Exception {
        perform(delete("/api/roles/{id}", adminRole.getId()), readOnlyUser).andExpect(status().isForbidden());
    }

    @Test
    void assignPermissions_addsThemToTheRole() throws Exception {
        perform(put("/api/roles/permissions"), admin, new RolePermissionDto(readOnlyRole.getId(), Set.of(writePermission.getId())))
                .andExpect(status().isNoContent());

        assertEquals(Set.of(readPermission, writePermission), roleRepository.findById(readOnlyRole.getId()).orElseThrow().getPermissions());
    }

    @Test
    void assignPermissions_toARoleTheCallerHolds_isForbidden() throws Exception {
        perform(put("/api/roles/permissions"), admin, new RolePermissionDto(adminRole.getId(), Set.of(readPermission.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignPermissions_unknownPermission_isNotFound() throws Exception {
        perform(put("/api/roles/permissions"), admin, new RolePermissionDto(readOnlyRole.getId(), Set.of(-1L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignPermissions_withoutUpdatePermission_isForbidden() throws Exception {
        perform(put("/api/roles/permissions"), readOnlyUser, new RolePermissionDto(adminRole.getId(), Set.of(readPermission.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void removePermissions_removesOnlyTheGivenOnes() throws Exception {
        perform(put("/api/roles/permissions"), admin, new RolePermissionDto(readOnlyRole.getId(), Set.of(writePermission.getId())))
                .andExpect(status().isNoContent());

        perform(delete("/api/roles/permissions"), admin, new RolePermissionDto(readOnlyRole.getId(), Set.of(readPermission.getId())))
                .andExpect(status().isNoContent());

        assertEquals(Set.of(writePermission), roleRepository.findById(readOnlyRole.getId()).orElseThrow().getPermissions());
    }

    @Test
    void removePermissions_withoutUpdatePermission_isForbidden() throws Exception {
        perform(delete("/api/roles/permissions"), readOnlyUser, new RolePermissionDto(adminRole.getId(), Set.of(readPermission.getId())))
                .andExpect(status().isForbidden());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, User actor) throws Exception {
        DefaultUserDetails userDetails = new DefaultUserDetails(actor);
        return mockMvc.perform(request.with(authentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()))));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, User actor, Object body) throws Exception {
        return perform(request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body)), actor);
    }

    private Permission permission(PermissionAction action) {
        Permission permission = new Permission();
        permission.setServiceName(IAM_SERVICE_NAME);
        permission.setAction(action);
        return permissionRepository.save(permission);
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
        user.getRoles().add(role);
        return userRepository.save(user);
    }
}
