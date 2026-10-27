package com.example.iamsystem.user.controller;

import com.example.iamsystem.email.dto.OutgoingEmail;
import com.example.iamsystem.email.service.EmailService;
import com.example.iamsystem.passwordreset.dto.PasswordResetDto;
import com.example.iamsystem.passwordreset.dto.PasswordResetRequestDto;
import com.example.iamsystem.passwordreset.repository.PasswordResetOTPRepository;
import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.repository.PermissionRepository;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.user.dto.PasswordChangeDto;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserLoginDto;
import com.example.iamsystem.user.dto.UserRoleAttachmentDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import com.example.iamsystem.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Set;

import static com.example.iamsystem.security.userdetails.TestActors.actingAs;
import static com.example.iamsystem.user.repository.TestUserLookup.findByUsername;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    /**
     * The management console client of the fixture tenant ({@code insert_user_role.sql}).
     */
    private static final String FIXTURE_CONSOLE_CLIENT_ID = "test-console-client-0001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PasswordResetOTPRepository otpRepository;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }


    @Test
    @DisplayName("Root creates a user in its own tenant")
    @Sql("/sql/insert_user_role.sql")
    void createUser_byRoot_createsUserInRootsTenant() throws Exception {
        User root = findByUsername(userRepository, "root").orElseThrow();
        UserCreationDto request = new UserCreationDto("newUser", "NewUser1!", "New User", "newuser@example.com", null);

        mockMvc.perform(post("/api/users")
                        .with(actingAs(root))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newUser"))
                .andExpect(jsonPath("$.tenantId").value(root.getTenant().getId()))
                .andExpect(jsonPath("$.createdBy").value("root"))
                .andExpect(jsonPath("$.rootUser").value(false));
    }

    @Test
    @DisplayName("Creating a user without users:create is forbidden")
    @Sql("/sql/insert_user_role.sql")
    void createUser_withoutWritePermission_isForbidden() throws Exception {
        User plainUser = findByUsername(userRepository, "user").orElseThrow();
        UserCreationDto request = new UserCreationDto("newUser", "NewUser1!", "New User", "newuser@example.com", null);

        mockMvc.perform(post("/api/users")
                        .with(actingAs(plainUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Root creates a management user when userType is MANAGEMENT")
    @Sql("/sql/insert_user_role.sql")
    void createUser_management_byRoot_isCreated() throws Exception {
        User root = findByUsername(userRepository, "root").orElseThrow();

        createUser(root, "manager", UserType.MANAGEMENT)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userType").value("MANAGEMENT"));
    }

    @Test
    @DisplayName("users:create creates regular users only; a management user also needs users:create-management")
    @Sql("/sql/insert_user_role.sql")
    void createUser_management_needsTheManagementKey() throws Exception {
        grantThroughRole("user", "users:create");
        User creator = findByUsername(userRepository, "user").orElseThrow();

        createUser(creator, "regular", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userType").value("REGULAR"));
        createUser(creator, "manager", UserType.MANAGEMENT).andExpect(status().isForbidden());
        assertTrue(findByUsername(userRepository, "manager").isEmpty());
    }

    @Test
    @DisplayName("A regular user cannot be given roles")
    @Sql("/sql/insert_user_role.sql")
    void assignRoles_toRegularUser_isBadRequest() throws Exception {
        User root = findByUsername(userRepository, "root").orElseThrow();
        createUser(root, "carol", UserType.REGULAR).andExpect(status().isCreated());

        attach(put("/api/users/roles"), root, Map.of("username", "carol", "roleIds", Set.of(1L)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A permission granted directly takes effect at once, and removing it takes it away")
    @Sql("/sql/insert_user_role.sql")
    void directPermissions_areGrantedAndRemoved() throws Exception {
        User root = findByUsername(userRepository, "root").orElseThrow();
        Map<String, Object> usersRead = Map.of("username", "user", "permissionIds", Set.of(catalogEntry("users:read").getId()));

        mockMvc.perform(get("/api/users").with(actingAs(findByUsername(userRepository, "user").orElseThrow())))
                .andExpect(status().isForbidden());

        attach(put("/api/users/permissions"), root, usersRead).andExpect(status().isNoContent());
        User member = findByUsername(userRepository, "user").orElseThrow();
        mockMvc.perform(get("/api/users").with(actingAs(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[1].permissionIds[0]").value(catalogEntry("users:read").getId()));

        attach(delete("/api/users/permissions"), root, usersRead).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/users").with(actingAs(member))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Nobody grants permissions to themselves, and regular users get none")
    @Sql("/sql/insert_user_role.sql")
    void directPermissions_toSelfOrRegularUser_areRefused() throws Exception {
        User root = findByUsername(userRepository, "root").orElseThrow();
        Set<Long> usersRead = Set.of(catalogEntry("users:read").getId());
        createUser(root, "carol", UserType.REGULAR).andExpect(status().isCreated());

        attach(put("/api/users/permissions"), root, Map.of("username", "root", "permissionIds", usersRead))
                .andExpect(status().isForbidden());
        attach(put("/api/users/permissions"), root, Map.of("username", "carol", "permissionIds", usersRead))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Root assigns a role to another user")
    @Sql("/sql/insert_user_role.sql")
    void assignRoles_successful() throws Exception {
        User currentUser = findByUsername(userRepository, "root").orElseThrow();

        UserRoleAttachmentDto userRoleAttachmentDto = new UserRoleAttachmentDto();
        userRoleAttachmentDto.setUsername("user");
        userRoleAttachmentDto.setRoleIds(Set.of(1L));

        mockMvc.perform(put("/api/users/roles")
                        .with(actingAs(currentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(userRoleAttachmentDto)))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    @DisplayName("Assigning roles to yourself is forbidden")
    @Sql("/sql/insert_user_role.sql")
    void assignRoles_toSelf_isForbidden() throws Exception {
        User currentUser = findByUsername(userRepository, "root").orElseThrow();

        UserRoleAttachmentDto userRoleAttachmentDto = new UserRoleAttachmentDto();
        userRoleAttachmentDto.setUsername("root");
        userRoleAttachmentDto.setRoleIds(Set.of(1L));

        mockMvc.perform(put("/api/users/roles")
                        .with(actingAs(currentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(userRoleAttachmentDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Root removes a role from another user")
    @Sql("/sql/insert_user_role.sql")
    void removeRoles_successful() throws Exception {
        User user = findByUsername(userRepository, "user").orElseThrow();
        Role testRole = roleRepository.findById(1L).orElseThrow();

        // Assign the role first so it can be removed
        user.addRole(testRole);
        userRepository.save(user);
        entityManager.detach(user);

        User currentUser = findByUsername(userRepository, "root").orElseThrow();

        UserRoleAttachmentDto userRoleAttachmentDto = new UserRoleAttachmentDto();
        userRoleAttachmentDto.setUsername("user");
        userRoleAttachmentDto.setRoleIds(Set.of(testRole.getId()));

        mockMvc.perform(delete("/api/users/roles")
                        .with(actingAs(currentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(userRoleAttachmentDto)))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    @DisplayName("Lists the tenant's users with paging information")
    @Sql("/sql/insert_user_role.sql")
    void getAllUsers_returnsTheTenantsUsers() throws Exception {
        User currentUser = findByUsername(userRepository, "root").orElseThrow();

        mockMvc.perform(get("/api/users")
                        .with(actingAs(currentUser))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].username").value(contains("root", "user")))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    @DisplayName("Returns the requested page of users")
    @Sql("/sql/insert_user_role.sql")
    void getAllUsers_returnsTheRequestedPage() throws Exception {
        User currentUser = findByUsername(userRepository, "root").orElseThrow();

        mockMvc.perform(get("/api/users")
                        .with(actingAs(currentUser))
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].username").value(contains("user")))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    @DisplayName("A negative page is a bad request")
    @Sql("/sql/insert_user_role.sql")
    void getAllUsers_withNegativePage_isBadRequest() throws Exception {
        User currentUser = findByUsername(userRepository, "root").orElseThrow();

        mockMvc.perform(get("/api/users")
                        .with(actingAs(currentUser))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A user is found by id")
    @Sql("/sql/insert_user_role.sql")
    void getUserById_successful() throws Exception {
        User currentUser = findByUsername(userRepository, "root").orElseThrow();
        User targetUser = findByUsername(userRepository, "user").orElseThrow();

        mockMvc.perform(get("/api/users/{id}", targetUser.getId())
                        .with(actingAs(currentUser))
                )
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @DisplayName("A user is found by username")
    @Sql("/sql/insert_user_role.sql")
    void getUserByUsername_successful() throws Exception {
        User currentUser = findByUsername(userRepository, "root").orElseThrow();

        mockMvc.perform(get("/api/users/by-username")
                        .with(actingAs(currentUser))
                        .param("username", "user"))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @DisplayName("A user is found by email")
    @Sql("/sql/insert_user_role.sql")
    void getUserByEmail_successful() throws Exception {
        User currentUser = findByUsername(userRepository, "root").orElseThrow();

        mockMvc.perform(get("/api/users/by-email")
                        .with(actingAs(currentUser))
                        .param("email", "user@test.com"))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @DisplayName("Root deletes another user")
    @Sql("/sql/insert_user_role.sql")
    void deleteUser_successful() throws Exception {
        User currentUser = findByUsername(userRepository, "root").orElseThrow();
        User targetUser = findByUsername(userRepository, "user").orElseThrow();

        mockMvc.perform(delete("/api/users/{id}", targetUser.getId())
                        .with(actingAs(currentUser)))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    @DisplayName("Deleting a user keeps the users they created")
    @Sql("/sql/insert_user_role.sql")
    void deleteUser_whoCreatedOthers_keepsThoseUsers() throws Exception {
        User root = findByUsername(userRepository, "root").orElseThrow();
        User creator = findByUsername(userRepository, "user").orElseThrow();
        User created = new User();
        created.setUsername("created");
        created.setEmail("created@test.com");
        created.setTenant(creator.getTenant());
        created.setCreatedBy(creator);
        userRepository.save(created);

        mockMvc.perform(delete("/api/users/{id}", creator.getId())
                        .with(actingAs(root)))
                .andExpect(status().isNoContent());

        assertNull(findByUsername(userRepository, "created").orElseThrow().getCreatedBy());
    }

    @Test
    @DisplayName("Deleting a user with a pending password-reset OTP also deletes the OTP")
    @Sql("/sql/insert_user_role.sql")
    void deleteUser_withPendingOtp_deletesTheOtp() throws Exception {
        User root = findByUsername(userRepository, "root").orElseThrow();
        User target = findByUsername(userRepository, "user").orElseThrow();
        mockMvc.perform(post("/api/password/reset-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new PasswordResetRequestDto(FIXTURE_CONSOLE_CLIENT_ID, "user"))))
                .andExpect(status().isOk());
        assertEquals(1, otpRepository.count());

        mockMvc.perform(delete("/api/users/{id}", target.getId())
                        .with(actingAs(root)))
                .andExpect(status().isNoContent());

        assertEquals(0, otpRepository.count());
    }

    @Test
    @DisplayName("The root user cannot delete itself")
    @Sql("/sql/insert_user_role.sql")
    void deleteUser_rootItself_isForbidden() throws Exception {
        User root = findByUsername(userRepository, "root").orElseThrow();

        mockMvc.perform(delete("/api/users/{id}", root.getId())
                        .with(actingAs(root)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("The root user can't be deleted"));
        assertTrue(userRepository.existsById(root.getId()));
    }

    @Test
    @DisplayName("A user allowed to delete users can't delete their own account")
    @Sql("/sql/insert_user_role.sql")
    void deleteUser_ownAccount_isForbidden() throws Exception {
        User root = findByUsername(userRepository, "root").orElseThrow();
        Map<String, Object> usersDelete = Map.of("username", "user", "permissionIds", Set.of(catalogEntry("users:delete").getId()));
        attach(put("/api/users/permissions"), root, usersDelete).andExpect(status().isNoContent());
        User member = findByUsername(userRepository, "user").orElseThrow();

        mockMvc.perform(delete("/api/users/{id}", member.getId())
                        .with(actingAs(member)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can't delete your own account"));
        assertTrue(userRepository.existsById(member.getId()));
    }

    @Test
    @DisplayName("Another user cannot delete the root user")
    @Sql("/sql/insert_user_role.sql")
    void deleteUser_rootByAnotherUser_isForbidden() throws Exception {
        User currentUser = findByUsername(userRepository, "user").orElseThrow();
        User root = findByUsername(userRepository, "root").orElseThrow();

        mockMvc.perform(delete("/api/users/{id}", root.getId())
                        .with(actingAs(currentUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A user changes their own password")
    @Sql("/sql/insert_user_role.sql")
    void changePassword_byUser_successful() throws Exception {
        User currentUser = findByUsername(userRepository, "user").orElseThrow();

        PasswordChangeDto passwordChangeDto = new PasswordChangeDto("P@ssw0rd", "NewPassword1!");

        mockMvc.perform(patch("/api/users/password")
                        .with(actingAs(currentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(passwordChangeDto)))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    @DisplayName("Changing your password without the old password fails validation")
    @Sql("/sql/insert_user_role.sql")
    void changePassword_withoutOldPassword_failsValidation() throws Exception {
        User currentUser = findByUsername(userRepository, "user").orElseThrow();

        mockMvc.perform(patch("/api/users/password")
                        .with(actingAs(currentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new PasswordChangeDto(null, "NewPassword1!"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("A wrong old password is a bad request")
    @Sql("/sql/insert_user_role.sql")
    void changePassword_byUser_invalidOldPassword() throws Exception {
        User currentUser = findByUsername(userRepository, "user").orElseThrow();

        PasswordChangeDto passwordChangeDto = new PasswordChangeDto("wrongPassword", "NewPassword1!");

        mockMvc.perform(patch("/api/users/password")
                        .with(actingAs(currentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(passwordChangeDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Root resets another user's password, also to an unverified address: the old one stops working, the emailed OTP sets a new one and verifies the address")
    @Sql("/sql/insert_user_role.sql")
    void resetPassword_byRoot_emailsAnOtpAndDisablesTheOldPassword() throws Exception {
        User root = findByUsername(userRepository, "root").orElseThrow();
        User member = findByUsername(userRepository, "user").orElseThrow();
        member.setEmailVerified(false);
        userRepository.save(member);

        mockMvc.perform(post("/api/users/{id}/password-reset", member.getId()).with(actingAs(root)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        ArgumentCaptor<OutgoingEmail> mail = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailService).send(mail.capture());
        assertEquals("user@test.com", mail.getValue().to());
        String otp = (String) mail.getValue().values().get("otp");
        login("user", "P@ssw0rd").andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new PasswordResetDto(FIXTURE_CONSOLE_CLIENT_ID, "user", otp, "Chosen-Password1!"))))
                .andExpect(status().isOk());
        login("user", "Chosen-Password1!").andExpect(status().isOk());
        assertTrue(findByUsername(userRepository, "user").orElseThrow().isEmailVerified());
    }

    @Test
    @DisplayName("When the reset email can't be sent, the admin gets 503 and the old password keeps working")
    @Sql("/sql/insert_user_role.sql")
    void resetPassword_whenTheEmailFails_changesNothing() throws Exception {
        User root = findByUsername(userRepository, "root").orElseThrow();
        User member = findByUsername(userRepository, "user").orElseThrow();
        doThrow(new MailSendException("SMTP down")).when(emailService).send(any(OutgoingEmail.class));

        mockMvc.perform(post("/api/users/{id}/password-reset", member.getId()).with(actingAs(root)))
                .andExpect(status().isServiceUnavailable());

        login("user", "P@ssw0rd").andExpect(status().isOk());
    }

    private ResultActions createUser(User creator, String username, UserType userType) throws Exception {
        UserCreationDto request = new UserCreationDto(username, "NewUser1!", "New User", username + "@example.com", userType);
        return mockMvc.perform(post("/api/users")
                .with(actingAs(creator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)));
    }

    private ResultActions attach(MockHttpServletRequestBuilder request, User actor, Object body) throws Exception {
        return mockMvc.perform(request
                .with(actingAs(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)));
    }

    private Permission catalogEntry(String name) {
        return permissionRepository.findAllByOrderByName().stream()
                .filter(permission -> permission.getName().equals(name))
                .findFirst().orElseThrow();
    }

    /**
     * Gives the fixture role (id 1) the permission and assigns the role to the user.
     */
    private void grantThroughRole(String username, String permissionName) {
        Role role = roleRepository.findById(1L).orElseThrow();
        role.getPermissions().add(catalogEntry(permissionName));
        roleRepository.save(role);
        User user = findByUsername(userRepository, username).orElseThrow();
        user.addRole(role);
        userRepository.save(user);
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new UserLoginDto(FIXTURE_CONSOLE_CLIENT_ID, username, password))));
    }

    @Test
    @DisplayName("Resetting a password without users:reset-password is forbidden")
    @Sql("/sql/insert_user_role.sql")
    void resetPassword_withoutPermission_isForbidden() throws Exception {
        User member = findByUsername(userRepository, "user").orElseThrow();
        User root = findByUsername(userRepository, "root").orElseThrow();

        mockMvc.perform(post("/api/users/{id}/password-reset", root.getId()).with(actingAs(member)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("Even with users:reset-password, nobody can reset the root user's password")
    @Sql("/sql/insert_user_role.sql")
    void resetPassword_ofRoot_isForbidden() throws Exception {
        Role resetter = roleRepository.findById(1L).orElseThrow();
        resetter.getPermissions().add(permissionRepository.findAllByOrderByName().stream()
                .filter(permission -> permission.getName().equals("users:reset-password"))
                .findFirst().orElseThrow());
        roleRepository.save(resetter);
        User member = findByUsername(userRepository, "user").orElseThrow();
        member.addRole(resetter);
        userRepository.save(member);
        User root = findByUsername(userRepository, "root").orElseThrow();

        mockMvc.perform(post("/api/users/{id}/password-reset", root.getId()).with(actingAs(findByUsername(userRepository, "user").orElseThrow())))
                .andExpect(status().isForbidden());
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("Resetting your own password is forbidden; you change it instead")
    @Sql("/sql/insert_user_role.sql")
    void resetPassword_ofSelf_isForbidden() throws Exception {
        User root = findByUsername(userRepository, "root").orElseThrow();

        mockMvc.perform(post("/api/users/{id}/password-reset", root.getId()).with(actingAs(root)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(emailService);
    }
}
