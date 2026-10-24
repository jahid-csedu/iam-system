package com.example.iamsystem.tenant;

import com.example.iamsystem.role.dto.RolePermissionDto;
import com.example.iamsystem.role.dto.RoleRequestDto;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.PasswordChangeDto;
import com.example.iamsystem.user.dto.UserRoleAttachmentDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
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
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two tenants, Acme and Globex, each with a root user, a member and a role. Acme's root holds every permission, yet
 * nothing of Globex is visible or changeable from Acme: other tenants' users and roles are reported as not found.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenantIsolationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;

    private User acmeRoot;
    private User acmeMember;
    private Role acmeRole;
    private User globexMember;
    private Role globexRole;

    @BeforeEach
    void setUp() {
        Tenant acme = tenantRepository.save(new Tenant("Acme", TenantStatus.ACTIVE));
        Tenant globex = tenantRepository.save(new Tenant("Globex", TenantStatus.ACTIVE));
        acmeRoot = user(acme, "acme-root", true);
        acmeMember = user(acme, "acme-member", false);
        acmeRole = role(acme, "MANAGER");
        user(globex, "globex-root", true);
        globexMember = user(globex, "globex-member", false);
        globexRole = role(globex, "MANAGER");
    }

    @Test
    @DisplayName("The user list contains only the caller's tenant")
    void userList_containsOnlyTheCallersTenant() throws Exception {
        perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].username").value(contains("acme-root", "acme-member")));
    }

    @Test
    @DisplayName("Users of another tenant are not found")
    void usersOfAnotherTenant_areNotFound() throws Exception {
        perform(get("/api/users/{id}", globexMember.getId())).andExpect(status().isNotFound());
        perform(get("/api/users/by-username").param("username", "globex-member")).andExpect(status().isNotFound());
        perform(get("/api/users/by-email").param("email", "globex-member@example.com")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Users of another tenant cannot be deleted or changed")
    void usersOfAnotherTenant_cannotBeChanged() throws Exception {
        perform(delete("/api/users/{id}", globexMember.getId())).andExpect(status().isNotFound());
        perform(patch("/api/users/password").param("username", "globex-member"), new PasswordChangeDto(null, "NewPassword1!"))
                .andExpect(status().isNotFound());
        perform(put("/api/users/roles"), roleAttachment("globex-member", globexRole.getId())).andExpect(status().isNotFound());

        assertTrue(userRepository.existsById(globexMember.getId()));
    }

    @Test
    @DisplayName("Roles of another tenant cannot be assigned")
    void rolesOfAnotherTenant_cannotBeAssigned() throws Exception {
        perform(put("/api/users/roles"), roleAttachment("acme-member", globexRole.getId())).andExpect(status().isNotFound());

        assertTrue(userRepository.findByUsername("acme-member").orElseThrow().getRoles().isEmpty());
    }

    @Test
    @DisplayName("The role list contains only the caller's tenant")
    void roleList_containsOnlyTheCallersTenant() throws Exception {
        perform(get("/api/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(acmeRole.getId().intValue())));
    }

    @Test
    @DisplayName("Roles of another tenant are not found or changeable")
    void rolesOfAnotherTenant_areNotFound() throws Exception {
        perform(get("/api/roles/{id}", globexRole.getId())).andExpect(status().isNotFound());
        perform(put("/api/roles/{id}", globexRole.getId()), new RoleRequestDto("HACKED", null)).andExpect(status().isNotFound());
        perform(delete("/api/roles/{id}", globexRole.getId())).andExpect(status().isNotFound());
        perform(put("/api/roles/permissions"), new RolePermissionDto(globexRole.getId(), Set.of(1L))).andExpect(status().isNotFound());

        assertEquals("MANAGER", roleRepository.findById(globexRole.getId()).orElseThrow().getName());
    }

    @Test
    @DisplayName("Role names are unique per tenant only")
    void roleNames_areUniquePerTenantOnly() throws Exception {
        perform(post("/api/roles"), new RoleRequestDto("AUDITOR", null)).andExpect(status().isCreated());
        perform(post("/api/roles"), new RoleRequestDto("AUDITOR", null)).andExpect(status().isBadRequest());
        perform(get("/api/roles/name/{name}", "MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(acmeRole.getId().intValue()));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(actingAs(acmeRoot)));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return perform(request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body)));
    }

    private User user(Tenant tenant, String username, boolean root) {
        User user = new User();
        user.setTenant(tenant);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setActive(true);
        user.setRootUser(root);
        return userRepository.save(user);
    }

    private Role role(Tenant tenant, String name) {
        Role role = new Role();
        role.setTenant(tenant);
        role.setName(name);
        return roleRepository.save(role);
    }

    private static UserRoleAttachmentDto roleAttachment(String username, Long roleId) {
        UserRoleAttachmentDto attachment = new UserRoleAttachmentDto();
        attachment.setUsername(username);
        attachment.setRoleIds(Set.of(roleId));
        return attachment;
    }
}
