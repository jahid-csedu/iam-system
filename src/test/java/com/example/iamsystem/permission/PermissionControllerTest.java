package com.example.iamsystem.permission;

import com.example.iamsystem.permission.model.Permission;
import com.example.iamsystem.permission.model.PermissionAction;
import com.example.iamsystem.permission.model.PermissionDto;
import com.example.iamsystem.role.RoleRepository;
import com.example.iamsystem.role.model.Role;
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
class PermissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
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
        readPermission = permissionRepository.save(new Permission(null, IAM_SERVICE_NAME, PermissionAction.READ, "Read access to IAM service", null, null));
        writePermission = permissionRepository.save(new Permission(null, IAM_SERVICE_NAME, PermissionAction.WRITE, "Write access to IAM service", null, null));
        updatePermission = permissionRepository.save(new Permission(null, IAM_SERVICE_NAME, PermissionAction.UPDATE, "Update access to IAM service", null, null));
        deletePermission = permissionRepository.save(new Permission(null, IAM_SERVICE_NAME, PermissionAction.DELETE, "Delete access to IAM service", null, null));

        // Create admin role with all permissions
        adminRole = new Role(null, "ADMIN", "Administrator role", new HashSet<>(), null, null);
        adminRole.getPermissions().add(readPermission);
        adminRole.getPermissions().add(writePermission);
        adminRole.getPermissions().add(updatePermission);
        adminRole.getPermissions().add(deletePermission);
        adminRole = roleRepository.save(adminRole);

        // Create a test user with admin role
        testUser = new User(null, "testuser", passwordEncoder.encode("password"), "Test User", "test@example.com", true, true, false, Instant.now().plusSeconds(1000), false, 0, null, new HashSet<>(), null, null, null);
        testUser.getRoles().add(adminRole);
        testUser = userRepository.save(testUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateUser(User user) {
        DefaultUserDetails userDetails = new DefaultUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    @Test
    void getAllPermissions_successful() throws Exception {
        authenticateUser(testUser);

        mockMvc.perform(get("/api/permissions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4))) // Expecting 4 permissions created in setup
                .andDo(print());
    }

    @Test
    void getAllPermissions_forbidden() throws Exception {
        // Create a user without any permissions
        User noPermissionUser = new User(null, "nopermission", passwordEncoder.encode("password"), "No Permission User", "nopermission@example.com", false, true, false, Instant.now().plusSeconds(1000), false, 0, null, new HashSet<>(), null, null, null);
        noPermissionUser = userRepository.save(noPermissionUser);
        authenticateUser(noPermissionUser);

        mockMvc.perform(get("/api/permissions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andDo(print());
    }

    @Test
    void createPermission_successful() throws Exception {
        authenticateUser(testUser);

        PermissionDto newPermissionDto = new PermissionDto(null, "NEW_SERVICE", PermissionAction.READ.name(), "New service read access");

        mockMvc.perform(post("/api/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newPermissionDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName", is("NEW_SERVICE")))
                .andExpect(jsonPath("$.action", is("READ")))
                .andDo(print());
    }

    @Test
    void createPermission_forbidden() throws Exception {
        // Create a user with only READ permission
        User readOnlyUser = new User(null, "readonly", passwordEncoder.encode("password"), "Read Only User", "readonly@example.com", false, true, false, Instant.now().plusSeconds(1000), false, 0, null, new HashSet<>(), null, null, null);
        Role readOnlyRole = new Role(null, "READ_ONLY", "Read only role", new HashSet<>(), null, null);
        readOnlyRole.getPermissions().add(readPermission);
        readOnlyRole = roleRepository.save(readOnlyRole);
        readOnlyUser.getRoles().add(readOnlyRole);
        readOnlyUser = userRepository.save(readOnlyUser);
        authenticateUser(readOnlyUser);

        PermissionDto newPermissionDto = new PermissionDto(null, "NEW_SERVICE", PermissionAction.WRITE.name(), "New service write access");

        mockMvc.perform(post("/api/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newPermissionDto)))
                .andExpect(status().isForbidden())
                .andDo(print());
    }

    @Test
    void getPermissionById_successful() throws Exception {
        authenticateUser(testUser);

        mockMvc.perform(get("/api/permissions/{id}", readPermission.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName", is(IAM_SERVICE_NAME)))
                .andExpect(jsonPath("$.action", is("READ")))
                .andDo(print());
    }

    @Test
    void getPermissionById_forbidden() throws Exception {
        User noPermissionUser = new User(null, "nopermission", passwordEncoder.encode("password"), "No Permission User", "nopermission@example.com", false, true, false, Instant.now().plusSeconds(1000), false, 0, null, new HashSet<>(), null, null, null);
        noPermissionUser = userRepository.save(noPermissionUser);
        authenticateUser(noPermissionUser);

        mockMvc.perform(get("/api/permissions/{id}", readPermission.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andDo(print());
    }

    @Test
    void updatePermission_successful() throws Exception {
        authenticateUser(testUser);

        PermissionDto updatedPermissionDto = new PermissionDto(updatePermission.getId(), IAM_SERVICE_NAME, PermissionAction.UPDATE.name(), "Updated description");

        mockMvc.perform(put("/api/permissions/{id}", updatePermission.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedPermissionDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", is("Updated description")))
                .andDo(print());
    }

    @Test
    void updatePermission_forbidden() throws Exception {
        User readOnlyUser = new User(null, "readonly", passwordEncoder.encode("password"), "Read Only User", "readonly@example.com", false, true, false, Instant.now().plusSeconds(1000), false, 0, null, new HashSet<>(), null, null, null);
        Role readOnlyRole = new Role(null, "READ_ONLY", "Read only role", new HashSet<>(), null, null);
        readOnlyRole.getPermissions().add(readPermission);
        readOnlyRole = roleRepository.save(readOnlyRole);
        readOnlyUser.getRoles().add(readOnlyRole);
        readOnlyUser = userRepository.save(readOnlyUser);
        authenticateUser(readOnlyUser);

        PermissionDto updatedPermissionDto = new PermissionDto(updatePermission.getId(), IAM_SERVICE_NAME, PermissionAction.UPDATE.name(), "Updated description");

        mockMvc.perform(put("/api/permissions/{id}", updatePermission.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedPermissionDto)))
                .andExpect(status().isForbidden())
                .andDo(print());
    }

    @Test
    void deletePermissionById_successful() throws Exception {
        authenticateUser(testUser);

        mockMvc.perform(delete("/api/permissions/{id}", deletePermission.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    void deletePermissionById_forbidden() throws Exception {
        User readOnlyUser = new User(null, "readonly", passwordEncoder.encode("password"), "Read Only User", "readonly@example.com", false, true, false, Instant.now().plusSeconds(1000), false, 0, null, new HashSet<>(), null, null, null);
        Role readOnlyRole = new Role(null, "READ_ONLY", "Read only role", new HashSet<>(), null, null);
        readOnlyRole.getPermissions().add(readPermission);
        readOnlyRole = roleRepository.save(readOnlyRole);
        readOnlyUser.getRoles().add(readOnlyRole);
        readOnlyUser = userRepository.save(readOnlyUser);
        authenticateUser(readOnlyUser);

        mockMvc.perform(delete("/api/permissions/{id}", deletePermission.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andDo(print());
    }

    @Test
    void getPermissionByServiceName_successful() throws Exception {
        authenticateUser(testUser);

        mockMvc.perform(get("/api/permissions/name/{serviceName}", IAM_SERVICE_NAME)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4))) // Expecting 4 permissions for IAM_SERVICE_NAME
                .andDo(print());
    }

    @Test
    void getPermissionByServiceName_forbidden() throws Exception {
        User noPermissionUser = new User(null, "nopermission", passwordEncoder.encode("password"), "No Permission User", "nopermission@example.com", false, true, false, Instant.now().plusSeconds(1000), false, 0, null, new HashSet<>(), null, null, null);
        noPermissionUser = userRepository.save(noPermissionUser);
        authenticateUser(noPermissionUser);

        mockMvc.perform(get("/api/permissions/name/{serviceName}", IAM_SERVICE_NAME)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andDo(print());
    }
}
