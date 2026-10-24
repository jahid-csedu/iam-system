package com.example.iamsystem.user.controller;

import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.security.userdetails.DefaultUserDetails;
import com.example.iamsystem.user.dto.PasswordChangeDto;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserRoleAttachmentDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
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
    @DisplayName("Root creates a user in its own tenant")
    @Sql("/sql/insert_user_role.sql")
    void createUser_byRoot_createsUserInRootsTenant() throws Exception {
        User root = userRepository.findByUsername("root").orElseThrow();
        UserCreationDto request = new UserCreationDto("newUser", "NewUser1!", "New User", "newuser@example.com");

        mockMvc.perform(post("/api/users")
                        .with(authentication(getAuthenticationToken(root)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newUser"))
                .andExpect(jsonPath("$.tenantId").value(root.getTenant().getId()))
                .andExpect(jsonPath("$.createdBy").value("root"))
                .andExpect(jsonPath("$.rootUser").value(false));
    }

    @Test
    @DisplayName("Creating a user without IAM:WRITE is forbidden")
    @Sql("/sql/insert_user_role.sql")
    void createUser_withoutWritePermission_isForbidden() throws Exception {
        User plainUser = userRepository.findByUsername("user").orElseThrow();
        UserCreationDto request = new UserCreationDto("newUser", "NewUser1!", "New User", "newuser@example.com");

        mockMvc.perform(post("/api/users")
                        .with(authentication(getAuthenticationToken(plainUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Root assigns a role to another user")
    @Sql("/sql/insert_user_role.sql")
    void assignRoles_successful() throws Exception {
        User currentUser = userRepository.findByUsername("root").orElseThrow();

        UserRoleAttachmentDto userRoleAttachmentDto = new UserRoleAttachmentDto();
        userRoleAttachmentDto.setUsername("user");
        userRoleAttachmentDto.setRoleIds(Set.of(1L));

        mockMvc.perform(put("/api/users/roles")
                        .with(authentication(getAuthenticationToken(currentUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(userRoleAttachmentDto)))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    @DisplayName("Assigning roles to yourself is forbidden")
    @Sql("/sql/insert_user_role.sql")
    void assignRoles_toSelf_isForbidden() throws Exception {
        User currentUser = userRepository.findByUsername("root").orElseThrow();

        UserRoleAttachmentDto userRoleAttachmentDto = new UserRoleAttachmentDto();
        userRoleAttachmentDto.setUsername("root");
        userRoleAttachmentDto.setRoleIds(Set.of(1L));

        mockMvc.perform(put("/api/users/roles")
                        .with(authentication(getAuthenticationToken(currentUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(userRoleAttachmentDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Root removes a role from another user")
    @Sql("/sql/insert_user_role.sql")
    void removeRoles_successful() throws Exception {
        User user = userRepository.findByUsername("user").orElseThrow();
        Role testRole = roleRepository.findById(1L).orElseThrow();

        // Assign the role first so it can be removed
        user.addRole(testRole);
        userRepository.save(user);
        entityManager.detach(user);

        User currentUser = userRepository.findByUsername("root").orElseThrow();

        UserRoleAttachmentDto userRoleAttachmentDto = new UserRoleAttachmentDto();
        userRoleAttachmentDto.setUsername("user");
        userRoleAttachmentDto.setRoleIds(Set.of(testRole.getId()));

        mockMvc.perform(delete("/api/users/roles")
                        .with(authentication(getAuthenticationToken(currentUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(userRoleAttachmentDto)))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    @DisplayName("Lists the tenant's users with paging information")
    @Sql("/sql/insert_user_role.sql")
    void getAllUsers_returnsTheTenantsUsers() throws Exception {
        User currentUser = userRepository.findByUsername("root").orElseThrow();

        mockMvc.perform(get("/api/users")
                        .with(authentication(getAuthenticationToken(currentUser)))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].username").value(contains("root", "user")))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    @DisplayName("Returns the requested page of users")
    @Sql("/sql/insert_user_role.sql")
    void getAllUsers_returnsTheRequestedPage() throws Exception {
        User currentUser = userRepository.findByUsername("root").orElseThrow();

        mockMvc.perform(get("/api/users")
                        .with(authentication(getAuthenticationToken(currentUser)))
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
        User currentUser = userRepository.findByUsername("root").orElseThrow();

        mockMvc.perform(get("/api/users")
                        .with(authentication(getAuthenticationToken(currentUser)))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A user is found by id")
    @Sql("/sql/insert_user_role.sql")
    void getUserById_successful() throws Exception {
        User currentUser = userRepository.findByUsername("root").orElseThrow();
        User targetUser = userRepository.findByUsername("user").orElseThrow();

        mockMvc.perform(get("/api/users/{id}", targetUser.getId())
                        .with(authentication(getAuthenticationToken(currentUser)))
                )
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @DisplayName("A user is found by username")
    @Sql("/sql/insert_user_role.sql")
    void getUserByUsername_successful() throws Exception {
        User currentUser = userRepository.findByUsername("root").orElseThrow();

        mockMvc.perform(get("/api/users/by-username")
                        .with(authentication(getAuthenticationToken(currentUser)))
                        .param("username", "user"))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @DisplayName("A user is found by email")
    @Sql("/sql/insert_user_role.sql")
    void getUserByEmail_successful() throws Exception {
        User currentUser = userRepository.findByUsername("root").orElseThrow();

        mockMvc.perform(get("/api/users/by-email")
                        .with(authentication(getAuthenticationToken(currentUser)))
                        .param("email", "user@test.com"))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @DisplayName("Root deletes another user")
    @Sql("/sql/insert_user_role.sql")
    void deleteUser_successful() throws Exception {
        User currentUser = userRepository.findByUsername("root").orElseThrow();
        User targetUser = userRepository.findByUsername("user").orElseThrow();

        mockMvc.perform(delete("/api/users/{id}", targetUser.getId())
                        .with(authentication(getAuthenticationToken(currentUser))))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    @DisplayName("Deleting a user keeps the users they created")
    @Sql("/sql/insert_user_role.sql")
    void deleteUser_whoCreatedOthers_keepsThoseUsers() throws Exception {
        User root = userRepository.findByUsername("root").orElseThrow();
        User creator = userRepository.findByUsername("user").orElseThrow();
        User created = new User();
        created.setUsername("created");
        created.setEmail("created@test.com");
        created.setTenant(creator.getTenant());
        created.setCreatedBy(creator);
        userRepository.save(created);

        mockMvc.perform(delete("/api/users/{id}", creator.getId())
                        .with(authentication(getAuthenticationToken(root))))
                .andExpect(status().isNoContent());

        assertNull(userRepository.findByUsername("created").orElseThrow().getCreatedBy());
    }

    @Test
    @DisplayName("Another user cannot delete the root user")
    @Sql("/sql/insert_user_role.sql")
    void deleteUser_rootByAnotherUser_isForbidden() throws Exception {
        User currentUser = userRepository.findByUsername("user").orElseThrow();
        User root = userRepository.findByUsername("root").orElseThrow();

        mockMvc.perform(delete("/api/users/{id}", root.getId())
                        .with(authentication(getAuthenticationToken(currentUser))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A user changes their own password")
    @Sql("/sql/insert_user_role.sql")
    void changePassword_byUser_successful() throws Exception {
        User currentUser = userRepository.findByUsername("user").orElseThrow();

        PasswordChangeDto passwordChangeDto = new PasswordChangeDto("P@ssw0rd", "NewPassword1!");

        mockMvc.perform(patch("/api/users/password")
                        .with(authentication(getAuthenticationToken(currentUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(passwordChangeDto)))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    @DisplayName("Root changes another user's password")
    @Sql("/sql/insert_user_role.sql")
    void changePassword_byRootUser_forAnotherUser_successful() throws Exception {
        User currentRootUser = userRepository.findByUsername("root").orElseThrow();
        User subordinateUser = userRepository.findByUsername("user").orElseThrow();

        PasswordChangeDto passwordChangeDto = new PasswordChangeDto(null, "NewPassword1!");

        mockMvc.perform(patch("/api/users/password")
                        .with(authentication(getAuthenticationToken(currentRootUser)))
                        .param("username", subordinateUser.getUsername())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(passwordChangeDto)))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    @DisplayName("A wrong old password is a bad request")
    @Sql("/sql/insert_user_role.sql")
    void changePassword_byUser_invalidOldPassword() throws Exception {
        User currentUser = userRepository.findByUsername("user").orElseThrow();

        PasswordChangeDto passwordChangeDto = new PasswordChangeDto("wrongPassword", "NewPassword1!");

        mockMvc.perform(patch("/api/users/password")
                        .with(authentication(getAuthenticationToken(currentUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(passwordChangeDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A regular user cannot change another user's password")
    @Sql("/sql/insert_user_role.sql")
    void changePassword_byUser_forAnotherUser_unauthorized() throws Exception {
        User currentUser = userRepository.findByUsername("user").orElseThrow();
        User rootUserForTest = userRepository.findByUsername("root").orElseThrow();

        PasswordChangeDto passwordChangeDto = new PasswordChangeDto(null, "NewPassword1!");

        mockMvc.perform(patch("/api/users/password")
                        .with(authentication(getAuthenticationToken(currentUser)))
                        .param("username", rootUserForTest.getUsername())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(passwordChangeDto)))
                .andExpect(status().isForbidden());
    }
}