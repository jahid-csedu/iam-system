package com.example.iamsystem.authorization;

import com.example.iamsystem.client.service.ClientService;
import com.example.iamsystem.permission.PermissionKey;
import com.example.iamsystem.platform.config.PlatformProperties;
import com.example.iamsystem.platform.config.PlatformProperties.RootUser;
import com.example.iamsystem.platform.service.PlatformBootstrap;
import com.example.iamsystem.role.service.PredefinedRoleService;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantType;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.tenant.service.TenantSignupService;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import com.example.iamsystem.user.repository.UserRepository;
import com.example.iamsystem.user.service.UserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.example.iamsystem.permission.PermissionKey.CLIENTS_READ;
import static com.example.iamsystem.permission.PermissionKey.PERMISSIONS_READ;
import static com.example.iamsystem.permission.PermissionKey.ROLES_READ;
import static com.example.iamsystem.permission.PermissionKey.USERS_ACTIVATE;
import static com.example.iamsystem.permission.PermissionKey.USERS_CREATE;
import static com.example.iamsystem.permission.PermissionKey.USERS_DEACTIVATE;
import static com.example.iamsystem.permission.PermissionKey.USERS_DELETE;
import static com.example.iamsystem.permission.PermissionKey.USERS_READ;
import static com.example.iamsystem.permission.PermissionKey.USERS_UPDATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;

/**
 * {@link AuthorizationService#permissionsOf} against MySQL: for every kind of actor it lists exactly the keys
 * {@link AuthorizationService#hasPermission} allows. Roles, direct permissions and grants are inserted directly, PLATFORM
 * keys included where the API would refuse them, to show they never count.
 */
@SpringBootTest
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class PermissionsOfTest {

    private static final String PASSWORD = "Password1!";

    @Autowired
    private AuthorizationService authorizationService;
    @Autowired
    private TenantSignupService tenantSignupService;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserFactory userFactory;
    @Autowired
    private PredefinedRoleService predefinedRoleService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Tenant platform;
    private long acmeId;
    private long globexId;

    @BeforeEach
    void setUp() {
        PlatformBootstrap bootstrap = new PlatformBootstrap(tenantRepository, predefinedRoleService, clientService,
                userRepository, userFactory, new PlatformProperties(new RootUser("platform-root", PASSWORD, "root@platform.test", null), false));
        transactionTemplate.executeWithoutResult(status -> bootstrap.run(null));
        platform = tenantRepository.findByType(TenantType.PLATFORM).orElseThrow();
        acmeId = signup("Acme", "acme-root");
        globexId = signup("Globex", "globex-root");
    }

    @Test
    @DisplayName("Tenant users hold exactly what the single checks allow: the root everything, others their roles and direct permissions")
    void tenantUsers_matchTheSingleChecks() {
        User alice = managementUser("alice", tenantRepository.findById(acmeId).orElseThrow());
        assignRole(alice, "USER_MANAGER", acmeId);
        grantDirectly(alice, "clients:read");
        grantDirectly(alice, "tenants:read");

        AuthenticatedActor aliceActor = actor(alice, acmeId);
        assertEquals(EnumSet.of(USERS_READ, USERS_CREATE, USERS_UPDATE, USERS_DELETE, USERS_DEACTIVATE, USERS_ACTIVATE, CLIENTS_READ),
                authorizationService.permissionsOf(aliceActor));
        assertMatchesTheSingleChecks(aliceActor);
        assertMatchesTheSingleChecks(actor(userRepository.findByUsernameAndTenantId("acme-root", acmeId).orElseThrow(), acmeId));
    }

    @Test
    @DisplayName("Platform users hold exactly what the single checks allow, at home and inside other tenants")
    void platformUsers_matchTheSingleChecks() {
        User support = managementUser("support", platform);
        assignRole(support, "VIEWER", platform.getId());
        addToRole("VIEWER", platform.getId(), "tenants:read");
        jdbcTemplate.update("""
                INSERT INTO platform_role_grants (user_id, role_id, tenant_id, created_at)
                SELECT ?, r.id, ?, NOW(6) FROM roles r WHERE r.name = 'VIEWER' AND r.tenant_id = ?
                """, support.getId(), acmeId, platform.getId());
        jdbcTemplate.update("""
                INSERT INTO platform_permission_grants (user_id, permission_id, tenant_id, created_at)
                SELECT ?, p.id, NULL, NOW(6) FROM permissions p WHERE p.name IN ('users:create', 'tenants:suspend')
                """, support.getId());

        AuthenticatedActor supportInAcme = actor(support, platform.getId()).actingIn(acmeId);
        assertEquals(EnumSet.of(USERS_READ, USERS_CREATE, ROLES_READ, CLIENTS_READ, PERMISSIONS_READ),
                authorizationService.permissionsOf(supportInAcme));
        assertEquals(EnumSet.of(USERS_CREATE), authorizationService.permissionsOf(actor(support, platform.getId()).actingIn(globexId)));
        for (AuthenticatedActor actor : List.of(supportInAcme, actor(support, platform.getId()),
                actor(support, platform.getId()).actingIn(globexId))) {
            assertMatchesTheSingleChecks(actor);
        }

        AuthenticatedActor platformRoot = actor(userRepository.findByUsernameAndTenantId("platform-root", platform.getId()).orElseThrow(),
                platform.getId());
        assertEquals(EnumSet.allOf(PermissionKey.class), authorizationService.permissionsOf(platformRoot));
        assertMatchesTheSingleChecks(platformRoot);
        assertMatchesTheSingleChecks(platformRoot.actingIn(acmeId));
    }

    private void assertMatchesTheSingleChecks(AuthenticatedActor actor) {
        Set<PermissionKey> allowedOneByOne = Arrays.stream(PermissionKey.values())
                .filter(permission -> authorizationService.hasPermission(actor, permission))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PermissionKey.class)));
        assertEquals(allowedOneByOne, authorizationService.permissionsOf(actor), actor.username() + " in tenant " + actor.tenantId());
    }

    private long signup(String tenantName, String rootUsername) {
        return tenantSignupService.signup(new TenantSignupDto(tenantName,
                new UserCreationDto(rootUsername, PASSWORD, null, rootUsername + "@" + tenantName.toLowerCase() + ".test", null))).tenant().id();
    }

    private User managementUser(String username, Tenant tenant) {
        return userRepository.save(userFactory.newUser(
                new UserCreationDto(username, PASSWORD, null, username + "@example.test", null), tenant, UserType.MANAGEMENT));
    }

    private static AuthenticatedActor actor(User user, long tenantId) {
        return new AuthenticatedActor(user.getId(), user.getUsername(), tenantId, user.isRootUser(), 1L);
    }

    private void assignRole(User user, String roleName, long tenantId) {
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = ? AND tenant_id = ?",
                user.getId(), roleName, tenantId);
    }

    private void addToRole(String roleName, long tenantId, String permission) {
        jdbcTemplate.update("""
                INSERT INTO role_permissions (role_id, permission_id)
                SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = ? AND r.tenant_id = ? AND p.name = ?
                """, roleName, tenantId, permission);
    }

    private void grantDirectly(User user, String permission) {
        jdbcTemplate.update("INSERT INTO user_permissions (user_id, permission_id) SELECT ?, id FROM permissions WHERE name = ?",
                user.getId(), permission);
    }
}
