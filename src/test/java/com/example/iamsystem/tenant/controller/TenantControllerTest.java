package com.example.iamsystem.tenant.controller;

import com.example.iamsystem.permission.PermissionKey;
import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.entity.PermissionScope;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.entity.RoleTemplate;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.role.repository.RoleTemplateRepository;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserLoginDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.example.iamsystem.security.userdetails.TestActors.actingAs;
import static com.example.iamsystem.user.repository.TestUserLookup.findByUsername;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs without a test transaction so that signup commits and rollbacks behave exactly as in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class TenantControllerTest {

    private static final Set<String> PREDEFINED_ROLES = Set.of("ADMIN", "USER_MANAGER", "VIEWER");

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

    @Autowired
    private RoleTemplateRepository roleTemplateRepository;

    @Test
    @DisplayName("Signup creates the tenant, its root user and its management console, through which root logs in")
    void signup_createsTenantWithItsRootUser_whoCanLogIn() throws Exception {
        String response = signup("Acme", "acme-root", "root@acme.com")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenant.name").value("Acme"))
                .andExpect(jsonPath("$.tenant.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rootUser.username").value("acme-root"))
                .andExpect(jsonPath("$.rootUser.rootUser").value(true))
                .andExpect(jsonPath("$.managementConsole.type").value("MANAGEMENT_CONSOLE"))
                .andReturn().getResponse().getContentAsString();
        String consoleClientId = jsonMapper.readTree(response).get("managementConsole").get("client_id").asString();

        assertEquals(24, consoleClientId.length());
        login(consoleClientId, "acme-root", "Password1!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty());
    }

    @Test
    @DisplayName("Usernames are unique per tenant: each tenant's 'bob' logs in through his own tenant's client")
    void usernames_areUniquePerTenant() throws Exception {
        String acmeConsole = consoleClientId(signup("Acme", "acme-root", "root@acme.com"));
        String globexConsole = consoleClientId(signup("Globex", "globex-root", "root@globex.com"));
        createManagementUser("acme-root", "bob", "Acme1234!", "bob@example.com").andExpect(status().isCreated());
        createManagementUser("globex-root", "bob", "Globex123!", "bob@example.com").andExpect(status().isCreated());

        login(acmeConsole, "bob", "Acme1234!").andExpect(status().isOk());
        login(globexConsole, "bob", "Globex123!").andExpect(status().isOk());
        login(globexConsole, "bob", "Acme1234!").andExpect(status().isUnauthorized());
        createManagementUser("acme-root", "bob", "Acme1234!", "bob2@example.com").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A root user's username and email can't be reused by any user of any tenant")
    void rootUsernameAndEmail_areUniqueAcrossTenants() throws Exception {
        signup("Acme", "acme-root", "root@acme.com").andExpect(status().isCreated());
        signup("Globex", "globex-root", "root@globex.com").andExpect(status().isCreated());

        createManagementUser("globex-root", "acme-root", "Globex123!", "other@globex.com").andExpect(status().isBadRequest());
        createManagementUser("globex-root", "carol", "Globex123!", "root@acme.com").andExpect(status().isBadRequest());
        createManagementUser("globex-root", "dave", "Globex123!", "dave@globex.com").andExpect(status().isCreated());
        signup("Initech", "dave", "root@initech.com").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A user can't log in through another tenant's client, nor through an unknown client")
    void login_throughForeignOrUnknownClient_isUnauthorized() throws Exception {
        signup("Acme", "acme-root", "root@acme.com").andExpect(status().isCreated());
        String globexConsole = consoleClientId(signup("Globex", "globex-root", "root@globex.com"));

        login(globexConsole, "acme-root", "Password1!").andExpect(status().isUnauthorized());
        login("unknown-client-id-000000", "acme-root", "Password1!").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Five wrong passwords lock the account inside its tenant")
    void failedLogins_lockTheAccount() throws Exception {
        String console = consoleClientId(signup("Acme", "acme-root", "root@acme.com"));

        for (int attempt = 0; attempt < 5; attempt++) {
            login(console, "acme-root", "Wrong123!").andExpect(status().isUnauthorized());
        }

        login(console, "acme-root", "Password1!").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Signup with a taken tenant name is rejected")
    void signup_withTakenTenantName_isRejected() throws Exception {
        signup("Acme", "acme-root", "root@acme.com").andExpect(status().isCreated());

        signup("Acme", "other-root", "other@acme.com").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Signup with an invalid root user leaves no tenant behind")
    void signup_whenRootUserIsInvalid_leavesNoTenantBehind() throws Exception {
        signup("Acme", "taken", "root@acme.com").andExpect(status().isCreated());

        signup("Beta", "taken", "root@beta.com").andExpect(status().isBadRequest());

        assertFalse(tenantRepository.existsByName("Beta"));
        assertEquals(PREDEFINED_ROLES.size(), roleRepository.count());
    }

    @Test
    @DisplayName("Signup gives the tenant its own ADMIN, USER_MANAGER and VIEWER roles")
    void signup_createsThePredefinedRoles() throws Exception {
        signup("Acme", "acme-root", "root@acme.com").andExpect(status().isCreated());

        Map<String, Set<String>> roles = rolesOf("Acme");

        assertEquals(PREDEFINED_ROLES, roles.keySet());
        assertEquals(allTenantKeys(), roles.get("ADMIN"));
        assertEquals(Set.of("users:read", "users:create", "users:update", "users:delete"), roles.get("USER_MANAGER"));
        assertEquals(Set.of("users:read", "roles:read", "clients:read", "permissions:read"), roles.get("VIEWER"));
    }

    @Test
    @DisplayName("Each tenant owns its copies: deleting one tenant's role leaves the other tenant's and the template untouched")
    void predefinedRoles_areIndependentPerTenant() throws Exception {
        signup("Acme", "acme-root", "root@acme.com").andExpect(status().isCreated());
        signup("Globex", "globex-root", "root@globex.com").andExpect(status().isCreated());

        roleRepository.delete(roleRepository.getRequiredByNameAndTenantId("VIEWER", tenantId("Acme")));

        assertFalse(rolesOf("Acme").containsKey("VIEWER"));
        assertEquals(PREDEFINED_ROLES, rolesOf("Globex").keySet());
        assertEquals(PREDEFINED_ROLES.size(), roleTemplateRepository.count());
    }

    @Test
    @DisplayName("The ADMIN template holds every TENANT permission key, and templates hold only TENANT permissions")
    void roleTemplates_coverTheCatalog() {
        RoleTemplate admin = roleTemplateRepository.findAll().stream()
                .filter(template -> template.getName().equals("ADMIN"))
                .findFirst().orElseThrow();

        assertEquals(allTenantKeys(), admin.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet()));
        roleTemplateRepository.findAll().forEach(template -> template.getPermissions()
                .forEach(permission -> assertEquals(PermissionScope.TENANT, permission.getScope())));
    }

    @Test
    @DisplayName("Signup without a tenant name fails validation")
    void signup_withoutTenantName_failsValidation() throws Exception {
        signup(" ", "acme-root", "root@acme.com").andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("The database allows only one root user per tenant")
    void database_allowsOnlyOneRootUserPerTenant() throws Exception {
        signup("Acme", "acme-root", "root@acme.com").andExpect(status().isCreated());
        Tenant acme = findByUsername(userRepository, "acme-root").orElseThrow().getTenant();

        User secondRoot = new User();
        secondRoot.setUsername("second-root");
        secondRoot.setEmail("second@acme.com");
        secondRoot.setTenant(acme);
        secondRoot.setRootUser(true);

        assertThrows(DataIntegrityViolationException.class, () -> userRepository.save(secondRoot));
    }

    private Long tenantId(String tenantName) {
        return tenantRepository.findAll().stream()
                .filter(tenant -> tenant.getName().equals(tenantName))
                .findFirst().orElseThrow().getId();
    }

    private Map<String, Set<String>> rolesOf(String tenantName) {
        return roleRepository.findAllByTenantIdOrderByName(tenantId(tenantName)).stream()
                .collect(Collectors.toMap(Role::getName,
                        role -> role.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet())));
    }

    private static Set<String> allTenantKeys() {
        return Arrays.stream(PermissionKey.values())
                .filter(key -> key.scope() == PermissionScope.TENANT)
                .map(PermissionKey::key)
                .collect(Collectors.toSet());
    }

    private String consoleClientId(ResultActions signup) throws Exception {
        String response = signup.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("managementConsole").get("client_id").asString();
    }

    private ResultActions login(String clientId, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new UserLoginDto(clientId, username, password))));
    }

    private ResultActions createManagementUser(String creator, String username, String password, String email) throws Exception {
        UserCreationDto request = new UserCreationDto(username, password, username, email, UserType.MANAGEMENT);
        return mockMvc.perform(post("/api/users")
                .with(actingAs(findByUsername(userRepository, creator).orElseThrow()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)));
    }

    private ResultActions signup(String tenantName, String username, String email) throws Exception {
        TenantSignupDto request = new TenantSignupDto(tenantName,
                new UserCreationDto(username, "Password1!", "Root User", email, null));
        return mockMvc.perform(post("/api/tenants/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)));
    }
}
