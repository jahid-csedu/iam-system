package com.example.iamsystem.user;

import com.example.iamsystem.role.RoleRepository;
import com.example.iamsystem.role.model.Role;
import com.example.iamsystem.security.user.DefaultUserDetails;
import com.example.iamsystem.user.model.dto.PasswordChangeDto;
import com.example.iamsystem.user.model.dto.UserRegistrationDto;
import com.example.iamsystem.user.model.dto.UserRoleAttachmentDto;
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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
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
    private ObjectMapper objectMapper;

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

    @Test
    @Sql("/sql/delete_user_role.sql")
    void userRegistration_successful() throws Exception {
        // No need to set security context for public registration
        UserRegistrationDto userRegistrationDto = new UserRegistrationDto(
                "newUser",
                "NewUser1!",
                null,
                "newuser@example.com",
                true,
                new HashSet<>()
        );

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRegistrationDto)))
                .andExpect(status().isCreated())
                .andDo(print());
    }

    @Test
    @Sql("/sql/insert_user_role.sql")
    void assignRoles_successful() throws Exception {
        User currentUser = userRepository.findByUsername("root").orElseThrow();
        DefaultUserDetails userDetails = new DefaultUserDetails(currentUser);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        UserRoleAttachmentDto userRoleAttachmentDto = new UserRoleAttachmentDto();
        userRoleAttachmentDto.setUsername("user");
        userRoleAttachmentDto.setRoleIds(Set.of(1L));

        mockMvc.perform(put("/api/users/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRoleAttachmentDto)))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    @Sql("/sql/insert_user_role.sql")
    void removeRoles_successful() throws Exception {
        User user = userRepository.findByUsername("user").orElseThrow();
        Role testRole = roleRepository.findById(1L).orElseThrow();

        // Assign the role first so it can be removed
        user.addRole(testRole);
        userRepository.save(user);
        entityManager.detach(user);

        User currentUser = userRepository.findByUsername("root").orElseThrow();
        DefaultUserDetails userDetails = new DefaultUserDetails(currentUser);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        UserRoleAttachmentDto userRoleAttachmentDto = new UserRoleAttachmentDto();
        userRoleAttachmentDto.setUsername("user");
        userRoleAttachmentDto.setRoleIds(Set.of(testRole.getId()));

        mockMvc.perform(delete("/api/users/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRoleAttachmentDto)))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    @Sql("/sql/insert_user_role.sql")
    void getAllUsers_successful() throws Exception {
        User currentUser = userRepository.findByUsername("root").orElseThrow();
        DefaultUserDetails userDetails = new DefaultUserDetails(currentUser);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @Sql("/sql/insert_user_role.sql")
    void getUserById_successful() throws Exception {
        User currentUser = userRepository.findByUsername("root").orElseThrow();
        User targetUser = userRepository.findByUsername("user").orElseThrow();
        DefaultUserDetails userDetails = new DefaultUserDetails(currentUser);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        mockMvc.perform(get("/api/users/{id}", targetUser.getId()))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @Sql("/sql/insert_user_role.sql")
    void getUserByUsername_successful() throws Exception {
        User currentUser = userRepository.findByUsername("root").orElseThrow();
        DefaultUserDetails userDetails = new DefaultUserDetails(currentUser);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        mockMvc.perform(get("/api/users/by-username")
                        .param("username", "user"))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @Sql("/sql/insert_user_role.sql")
    void getUserByEmail_successful() throws Exception {
        User currentUser = userRepository.findByUsername("root").orElseThrow();
        DefaultUserDetails userDetails = new DefaultUserDetails(currentUser);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        mockMvc.perform(get("/api/users/by-email")
                        .param("email", "user@test.com"))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @Sql("/sql/insert_user_role.sql")
    void deleteUser_successful() throws Exception {
        User currentUser = userRepository.findByUsername("root").orElseThrow();
        User targetUser = userRepository.findByUsername("user").orElseThrow();
        DefaultUserDetails userDetails = new DefaultUserDetails(currentUser);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        mockMvc.perform(delete("/api/users/{id}", targetUser.getId()))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    @Sql("/sql/insert_user_role.sql")
    void changePassword_byUser_successful() throws Exception {
        User currentUser = userRepository.findByUsername("user").orElseThrow();
        DefaultUserDetails userDetails = new DefaultUserDetails(currentUser);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        PasswordChangeDto passwordChangeDto = new PasswordChangeDto("P@ssw0rd", "NewPassword1!");

        mockMvc.perform(patch("/api/users/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(passwordChangeDto)))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    @Sql("/sql/insert_user_role.sql")
    void changePassword_byRootUser_forSubordinate_successful() throws Exception {
        User currentRootUser = userRepository.findByUsername("root").orElseThrow();
        User subordinateUser = userRepository.findByUsername("user").orElseThrow();

        DefaultUserDetails rootUserDetails = new DefaultUserDetails(currentRootUser);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(rootUserDetails, null, rootUserDetails.getAuthorities()));

        PasswordChangeDto passwordChangeDto = new PasswordChangeDto(null, "NewPassword1!");

        mockMvc.perform(patch("/api/users/password")
                        .param("userId", String.valueOf(subordinateUser.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(passwordChangeDto)))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    @Sql("/sql/insert_user_role.sql")
    void changePassword_byUser_invalidOldPassword() throws Exception {
        User currentUser = userRepository.findByUsername("user").orElseThrow();
        DefaultUserDetails userDetails = new DefaultUserDetails(currentUser);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        PasswordChangeDto passwordChangeDto = new PasswordChangeDto("wrongPassword", "NewPassword1!");

        mockMvc.perform(patch("/api/users/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(passwordChangeDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Sql("/sql/insert_user_role.sql")
    void changePassword_byUser_forAnotherUser_unauthorized() throws Exception {
        User currentUser = userRepository.findByUsername("user").orElseThrow();
        User rootUserForTest = userRepository.findByUsername("root").orElseThrow();

        DefaultUserDetails userDetails = new DefaultUserDetails(currentUser);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        PasswordChangeDto passwordChangeDto = new PasswordChangeDto(null, "NewPassword1!");

        mockMvc.perform(patch("/api/users/password")
                        .param("userId", String.valueOf(rootUserForTest.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(passwordChangeDto)))
                .andExpect(status().isForbidden());
    }
}