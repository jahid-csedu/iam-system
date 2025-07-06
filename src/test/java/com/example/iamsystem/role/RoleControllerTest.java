package com.example.iamsystem.role;

import com.example.iamsystem.permission.PermissionRepository;
import com.example.iamsystem.permission.model.Permission;
import com.example.iamsystem.permission.model.PermissionAction;
import com.example.iamsystem.role.model.Role;
import com.example.iamsystem.role.model.RoleDto;
import com.example.iamsystem.role.model.RolePermissionDto;
import com.example.iamsystem.security.user.DefaultUserDetails;
import com.example.iamsystem.user.UserRepository;
import com.example.iamsystem.user.model.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static com.example.iamsystem.constant.PermissionConstants.IAM_SERVICE_NAME;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private User readOnlyUser;
    private Role adminRole;
    private Permission readPermission;
    private Permission writePermission;
    private Permission updatePermission;
    private Permission deletePermission;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();

        // Create permissions
        readPermission = new Permission();
        readPermission.setAction(PermissionAction.READ);
        readPermission.setServiceName(IAM_SERVICE_NAME);
        readPermission.setDescription("Read access to IAM service");
        readPermission = permissionRepository.save(readPermission);

        writePermission = new Permission();
        writePermission.setAction(PermissionAction.WRITE);
        writePermission.setServiceName(IAM_SERVICE_NAME);
        writePermission.setDescription("Write access to IAM service");
        writePermission = permissionRepository.save(writePermission);

        updatePermission = new Permission();
        updatePermission.setAction(PermissionAction.UPDATE);
        updatePermission.setServiceName(IAM_SERVICE_NAME);
        updatePermission.setDescription("Update access to IAM service");
        updatePermission = permissionRepository.save(updatePermission);

        deletePermission = new Permission();
        deletePermission.setAction(PermissionAction.DELETE);
        deletePermission.setServiceName(IAM_SERVICE_NAME);
        deletePermission.setDescription("Delete access to IAM service");
        deletePermission = permissionRepository.save(deletePermission);

        // Create admin role with all permissions
        adminRole = new Role();
        adminRole.setName("ADMIN");
        adminRole.setDescription("Administrator role");
        adminRole.setPermissions(new HashSet<>());
        adminRole.getPermissions().add(readPermission);
        adminRole.getPermissions().add(writePermission);
        adminRole.getPermissions().add(updatePermission);
        adminRole.getPermissions().add(deletePermission);
        adminRole = roleRepository.save(adminRole);

        // Create a test user with admin role
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setPassword(passwordEncoder.encode("password"));
        testUser.setFullName("Test User");
        testUser.setEmail("test@example.com");
        testUser.setActive(true);
        testUser.setRoles(new HashSet<>());
        testUser.getRoles().add(adminRole);
        testUser = userRepository.save(testUser);

        // Create a read-only user
        Role readOnlyRole = new Role();
        readOnlyRole.setName("READ_ONLY");
        readOnlyRole.setDescription("Read only role");
        readOnlyRole.setPermissions(new HashSet<>());
        readOnlyRole.getPermissions().add(readPermission);
        readOnlyRole = roleRepository.save(readOnlyRole);

        readOnlyUser = new User();
        readOnlyUser.setUsername("readonlyuser");
        readOnlyUser.setPassword(passwordEncoder.encode("password"));
        readOnlyUser.setFullName("Read Only User");
        readOnlyUser.setEmail("readonly@example.com");
        readOnlyUser.setActive(true);
        readOnlyUser.setRoles(new HashSet<>());
        readOnlyUser.getRoles().add(readOnlyRole);
        readOnlyUser = userRepository.save(readOnlyUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
    }

    private void authenticateUser(User user) {
        DefaultUserDetails userDetails = new DefaultUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    @Test
    void getAllRoles_successful() throws Exception {
        authenticateUser(testUser);

        mockMvc.perform(get("/api/roles")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))) // ADMIN and READ_ONLY roles
                .andDo(print());
    }

    @Test
    void getAllRoles_forbidden() throws Exception {
        // No authentication
        mockMvc.perform(get("/api/roles")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andDo(print());
    }

    @Test
    void createRole_successful() throws Exception {
        authenticateUser(testUser);

        RoleDto newRoleDto = new RoleDto(null, "NEW_ROLE", "A newly created role", new HashSet<>());

        mockMvc.perform(post("/api/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newRoleDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("NEW_ROLE")))
                .andDo(print());
    }

    @Test
    void createRole_forbidden() throws Exception {
        authenticateUser(readOnlyUser);

        RoleDto newRoleDto = new RoleDto(null, "NEW_ROLE", "A newly created role", new HashSet<>());

        mockMvc.perform(post("/api/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newRoleDto)))
                .andExpect(status().isForbidden())
                .andDo(print());
    }

    @Test
    void getRoleById_successful() throws Exception {
        authenticateUser(testUser);

        mockMvc.perform(get("/api/roles/{id}", adminRole.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("ADMIN")))
                .andDo(print());
    }

    @Test
    void updateRole_successful() throws Exception {
        authenticateUser(testUser);

        RoleDto updatedRoleDto = new RoleDto(adminRole.getId(), "ADMIN_UPDATED", "Updated description", new HashSet<>());

        mockMvc.perform(put("/api/roles/{id}", adminRole.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedRoleDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("ADMIN_UPDATED")))
                .andExpect(jsonPath("$.description", is("Updated description")))
                .andDo(print());
    }

    @Test
    void updateRole_forbidden() throws Exception {
        authenticateUser(readOnlyUser);

        RoleDto updatedRoleDto = new RoleDto(adminRole.getId(), "ADMIN_UPDATED", "Updated description", new HashSet<>());

        mockMvc.perform(put("/api/roles/{id}", adminRole.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedRoleDto)))
                .andExpect(status().isForbidden())
                .andDo(print());
    }

    @Test
    void deleteRoleById_successful() throws Exception {
        authenticateUser(testUser);

        // Create a role to delete
        Role roleToDelete = new Role();
        roleToDelete.setName("TO_DELETE");
        roleToDelete.setDescription("Role to be deleted");
        roleToDelete.setPermissions(new HashSet<>());
        roleToDelete = roleRepository.save(roleToDelete);

        mockMvc.perform(delete("/api/roles/{id}", roleToDelete.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    void deleteRoleById_forbidden() throws Exception {
        authenticateUser(readOnlyUser);

        mockMvc.perform(delete("/api/roles/{id}", adminRole.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andDo(print());
    }

    @Test
    void getRoleByName_successful() throws Exception {
        authenticateUser(testUser);

        mockMvc.perform(get("/api/roles/name/{name}", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("ADMIN")))
                .andDo(print());
    }

    @Test
    void assignPermissions_successful() throws Exception {
        authenticateUser(testUser);

        // Create a new role to assign permissions to
        Role newRole = new Role();
        newRole.setName("NEW_ROLE_FOR_PERMS");
        newRole.setDescription("");
        newRole.setPermissions(new HashSet<>());
        newRole = roleRepository.save(newRole);

        RolePermissionDto rolePermissionDto = new RolePermissionDto();
        rolePermissionDto.setRoleId(newRole.getId());
        rolePermissionDto.setPermissionIds(Set.of(readPermission.getId(), writePermission.getId()));

        mockMvc.perform(put("/api/roles/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rolePermissionDto)))
                .andExpect(status().isNoContent())
                .andDo(print());

        // Verify permissions are assigned
        Role updatedRole = roleRepository.findById(newRole.getId()).orElseThrow();
        Set<Long> assignedPermissionIds = new HashSet<>();
        updatedRole.getPermissions().forEach(p -> assignedPermissionIds.add(p.getId()));
        assert (assignedPermissionIds.contains(readPermission.getId()));
        assert (assignedPermissionIds.contains(writePermission.getId()));
    }

    @Test
    void assignPermissions_forbidden() throws Exception {
        authenticateUser(readOnlyUser);

        RolePermissionDto rolePermissionDto = new RolePermissionDto();
        rolePermissionDto.setRoleId(adminRole.getId());
        rolePermissionDto.setPermissionIds(Set.of(readPermission.getId()));

        mockMvc.perform(put("/api/roles/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rolePermissionDto)))
                .andExpect(status().isForbidden())
                .andDo(print());
    }

    @Test
    void removePermissions_successful() throws Exception {
        authenticateUser(testUser);

        // Assign some permissions first
        adminRole.getPermissions().add(readPermission);
        adminRole.getPermissions().add(writePermission);
        roleRepository.save(adminRole);

        RolePermissionDto rolePermissionDto = new RolePermissionDto();
        rolePermissionDto.setRoleId(adminRole.getId());
        rolePermissionDto.setPermissionIds(Set.of(readPermission.getId()));

        mockMvc.perform(delete("/api/roles/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rolePermissionDto)))
                .andExpect(status().isNoContent())
                .andDo(print());

        // Verify permissions are removed
        Role updatedRole = roleRepository.findById(adminRole.getId()).orElseThrow();
        Set<Long> assignedPermissionIds = new HashSet<>();
        updatedRole.getPermissions().forEach(p -> assignedPermissionIds.add(p.getId()));
        assert (!assignedPermissionIds.contains(readPermission.getId()));
        assert (assignedPermissionIds.contains(writePermission.getId())); // writePermission should still be there
    }

    @Test
    void removePermissions_forbidden() throws Exception {
        authenticateUser(readOnlyUser);

        RolePermissionDto rolePermissionDto = new RolePermissionDto();
        rolePermissionDto.setRoleId(adminRole.getId());
        rolePermissionDto.setPermissionIds(Set.of(readPermission.getId()));

        mockMvc.perform(delete("/api/roles/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rolePermissionDto)))
                .andExpect(status().isForbidden())
                .andDo(print());
    }
}
