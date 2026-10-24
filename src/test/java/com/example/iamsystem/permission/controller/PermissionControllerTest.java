package com.example.iamsystem.permission.controller;

import com.example.iamsystem.permission.dto.PermissionDto;
import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.entity.PermissionAction;
import com.example.iamsystem.permission.repository.PermissionRepository;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.security.userdetails.DefaultUserDetails;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;

import static com.example.iamsystem.permission.PermissionConstants.IAM_SERVICE_NAME;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
    private JsonMapper jsonMapper;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private Tenant tenant;
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
        tenant = tenantRepository.save(new Tenant("Permission Test Tenant", TenantStatus.ACTIVE));

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
        testUser.setTenant(tenant);
        testUser.setUsername("testuser");
        testUser.setPassword(passwordEncoder.encode("password"));
        testUser.setFullName("Test User");
        testUser.setEmail("test@example.com");
        testUser.setActive(true);
        testUser.setRoles(new HashSet<>());
        testUser.getRoles().add(adminRole);
        testUser = userRepository.save(testUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private UsernamePasswordAuthenticationToken getAuthenticationToken(User user) {
        DefaultUserDetails userDetails = new DefaultUserDetails(user);
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Test
    void getAllPermissions_successful() throws Exception {

        mockMvc.perform(get("/api/permissions")
                        .with(authentication(getAuthenticationToken(testUser)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andDo(print());
    }

    @Test
    void getAllPermissions_forbidden() throws Exception {
        // Create a user without any permissions
        User noPermissionUser = new User();
        noPermissionUser.setTenant(tenant);
        noPermissionUser.setUsername("nopermission");
        noPermissionUser.setPassword(passwordEncoder.encode("password"));
        noPermissionUser.setFullName("No Permission User");
        noPermissionUser.setEmail("nopermission@example.com");
        noPermissionUser.setActive(true);
        noPermissionUser.setRoles(new HashSet<>());
        noPermissionUser = userRepository.save(noPermissionUser);

        mockMvc.perform(get("/api/permissions")
                        .with(authentication(getAuthenticationToken(noPermissionUser)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andDo(print());
    }

    @Test
    void createPermission_successful() throws Exception {
        PermissionDto newPermissionDto = new PermissionDto(null, "NEW_SERVICE", PermissionAction.READ.name(),
                "New service read access");

        mockMvc.perform(post("/api/permissions")
                        .with(authentication(getAuthenticationToken(testUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(newPermissionDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName", is("NEW_SERVICE")))
                .andExpect(jsonPath("$.action", is("READ")))
                .andDo(print());
    }

    @Test
    void createPermission_forbidden() throws Exception {
        // Create a user with only READ permission
        User readOnlyUser = new User();
        readOnlyUser.setTenant(tenant);
        readOnlyUser.setUsername("readonly");
        readOnlyUser.setPassword(passwordEncoder.encode("password"));
        readOnlyUser.setFullName("Read Only User");
        readOnlyUser.setEmail("readonly@example.com");
        readOnlyUser.setActive(true);
        readOnlyUser.setRoles(new HashSet<>());

        Role readOnlyRole = new Role();
        readOnlyRole.setName("READ_ONLY");
        readOnlyRole.setDescription("Read only role");
        readOnlyRole.setPermissions(new HashSet<>());
        readOnlyRole.getPermissions().add(readPermission);
        readOnlyRole = roleRepository.save(readOnlyRole);

        readOnlyUser.getRoles().add(readOnlyRole);
        readOnlyUser = userRepository.save(readOnlyUser);

        PermissionDto newPermissionDto = new PermissionDto(null, "NEW_SERVICE", PermissionAction.WRITE.name(),
                "New service write access");

        mockMvc.perform(post("/api/permissions")
                        .with(authentication(getAuthenticationToken(readOnlyUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(newPermissionDto)))
                .andExpect(status().isForbidden())
                .andDo(print());
    }

    @Test
    void getPermissionById_successful() throws Exception {
        mockMvc.perform(get("/api/permissions/{id}", readPermission.getId())
                        .with(authentication(getAuthenticationToken(testUser)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName", is(IAM_SERVICE_NAME)))
                .andExpect(jsonPath("$.action", is("READ")))
                .andDo(print());
    }

    @Test
    void getPermissionById_forbidden() throws Exception {
        User noPermissionUser = new User();
        noPermissionUser.setTenant(tenant);
        noPermissionUser.setUsername("nopermission");
        noPermissionUser.setPassword(passwordEncoder.encode("password"));
        noPermissionUser.setFullName("No Permission User");
        noPermissionUser.setEmail("nopermission@example.com");
        noPermissionUser.setActive(true);
        noPermissionUser.setRoles(new HashSet<>());
        noPermissionUser = userRepository.save(noPermissionUser);

        mockMvc.perform(get("/api/permissions/{id}", readPermission.getId())
                        .with(authentication(getAuthenticationToken(noPermissionUser)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andDo(print());
    }

    @Test
    void updatePermission_successful() throws Exception {
        PermissionDto updatedPermissionDto = new PermissionDto(updatePermission.getId(), IAM_SERVICE_NAME,
                PermissionAction.UPDATE.name(), "Updated description");

        mockMvc.perform(put("/api/permissions/{id}", updatePermission.getId())
                        .with(authentication(getAuthenticationToken(testUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(updatedPermissionDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", is("Updated description")))
                .andDo(print());
    }

    @Test
    void updatePermission_forbidden() throws Exception {
        User readOnlyUser = new User();
        readOnlyUser.setTenant(tenant);
        readOnlyUser.setUsername("readonly");
        readOnlyUser.setPassword(passwordEncoder.encode("password"));
        readOnlyUser.setFullName("Read Only User");
        readOnlyUser.setEmail("readonly@example.com");
        readOnlyUser.setActive(true);
        readOnlyUser.setRoles(new HashSet<>());

        Role readOnlyRole = new Role();
        readOnlyRole.setName("READ_ONLY");
        readOnlyRole.setDescription("Read only role");
        readOnlyRole.setPermissions(new HashSet<>());
        readOnlyRole.getPermissions().add(readPermission);
        readOnlyRole = roleRepository.save(readOnlyRole);

        readOnlyUser.getRoles().add(readOnlyRole);
        readOnlyUser = userRepository.save(readOnlyUser);

        PermissionDto updatedPermissionDto = new PermissionDto(updatePermission.getId(), IAM_SERVICE_NAME,
                PermissionAction.UPDATE.name(), "Updated description");

        mockMvc.perform(put("/api/permissions/{id}", updatePermission.getId())
                        .with(authentication(getAuthenticationToken(readOnlyUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(updatedPermissionDto)))
                .andExpect(status().isForbidden())
                .andDo(print());
    }

    @Test
    void deletePermissionById_successful() throws Exception {
        mockMvc.perform(delete("/api/permissions/{id}", deletePermission.getId())
                        .with(authentication(getAuthenticationToken(testUser)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    void deletePermissionById_forbidden() throws Exception {
        User readOnlyUser = new User();
        readOnlyUser.setTenant(tenant);
        readOnlyUser.setUsername("readonly");
        readOnlyUser.setPassword(passwordEncoder.encode("password"));
        readOnlyUser.setFullName("Read Only User");
        readOnlyUser.setEmail("readonly@example.com");
        readOnlyUser.setActive(true);
        readOnlyUser.setRoles(new HashSet<>());

        Role readOnlyRole = new Role();
        readOnlyRole.setName("READ_ONLY");
        readOnlyRole.setDescription("Read only role");
        readOnlyRole.setPermissions(new HashSet<>());
        readOnlyRole.getPermissions().add(readPermission);
        readOnlyRole = roleRepository.save(readOnlyRole);

        readOnlyUser.getRoles().add(readOnlyRole);
        readOnlyUser = userRepository.save(readOnlyUser);

        mockMvc.perform(delete("/api/permissions/{id}", deletePermission.getId())
                        .with(authentication(getAuthenticationToken(readOnlyUser)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andDo(print());
    }

    @Test
    void getPermissionByServiceName_successful() throws Exception {
        mockMvc.perform(get("/api/permissions/name/{serviceName}", IAM_SERVICE_NAME)
                        .with(authentication(getAuthenticationToken(testUser)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4))) // Expecting 4 permissions for IAM_SERVICE_NAME
                .andDo(print());
    }

    @Test
    void getPermissionByServiceName_forbidden() throws Exception {
        User noPermissionUser = new User();
        noPermissionUser.setTenant(tenant);
        noPermissionUser.setUsername("nopermission");
        noPermissionUser.setPassword(passwordEncoder.encode("password"));
        noPermissionUser.setFullName("No Permission User");
        noPermissionUser.setEmail("nopermission@example.com");
        noPermissionUser.setActive(true);
        noPermissionUser.setRoles(new HashSet<>());
        noPermissionUser = userRepository.save(noPermissionUser);

        mockMvc.perform(get("/api/permissions/name/{serviceName}", IAM_SERVICE_NAME)
                        .with(authentication(getAuthenticationToken(noPermissionUser)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andDo(print());
    }
}
