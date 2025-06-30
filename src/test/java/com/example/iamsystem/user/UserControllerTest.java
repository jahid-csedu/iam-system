package com.example.iamsystem.user;

import com.example.iamsystem.security.user.DefaultUserDetails;
import com.example.iamsystem.user.model.dto.PasswordChangeDto;
import com.example.iamsystem.user.model.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        User newRootUser = new User();
        newRootUser.setUsername("root");
        newRootUser.setPassword(passwordEncoder.encode("password"));
        newRootUser.setEmail("root@example.com");
        newRootUser.setRootUser(true);
        userRepository.save(newRootUser);

        User newUser = new User();
        newUser.setUsername("user");
        newUser.setPassword(passwordEncoder.encode("password"));
        newUser.setEmail("user@example.com");
        newUser.setCreatedBy(newRootUser);
        userRepository.save(newUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void changePassword_byUser_successful() throws Exception {
        User currentUser = userRepository.findByUsername("user").orElseThrow();
        DefaultUserDetails userDetails = new DefaultUserDetails(currentUser);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        PasswordChangeDto passwordChangeDto = new PasswordChangeDto("password", "NewPassword1!");

        mockMvc.perform(patch("/api/users/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(passwordChangeDto)))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
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