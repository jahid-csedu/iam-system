package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.InvalidPasswordException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.user.config.PasswordExpirationProperties;
import com.example.iamsystem.user.dto.PasswordChangeDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static com.example.iamsystem.user.service.UserTestData.TENANT;
import static com.example.iamsystem.user.service.UserTestData.createdBy;
import static com.example.iamsystem.user.service.UserTestData.rootUser;
import static com.example.iamsystem.user.service.UserTestData.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPasswordServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserValidator userValidator;
    @Mock
    private UserAccessPolicy accessPolicy;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserPasswordService service;
    private User root;
    private User child;

    @BeforeEach
    void setUp() {
        service = new UserPasswordService(userRepository, userValidator, accessPolicy, currentUserProvider,
                passwordEncoder, new PasswordExpirationProperties(90));
        root = rootUser(1, "root");
        child = createdBy(root, 2, "child");
    }

    @Test
    @DisplayName("The correct old password updates the password and its expiry")
    void changeOwnPassword_withCorrectOldPassword_updatesPasswordAndExpiry() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(child);
        when(passwordEncoder.matches("old", "encoded-child")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("encoded-new");

        service.changePassword(new PasswordChangeDto("old", "NewPassword1!"), null);

        assertEquals("encoded-new", child.getPassword());
        assertTrue(child.getPasswordExpiryDate().isAfter(Instant.now()));
        verify(userRepository).save(child);
    }

    @Test
    @DisplayName("A wrong old password is rejected")
    void changeOwnPassword_withWrongOldPassword_isRejected() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(child);
        when(passwordEncoder.matches("wrong", "encoded-child")).thenReturn(false);

        assertThrows(InvalidPasswordException.class,
                () -> service.changePassword(new PasswordChangeDto("wrong", "NewPassword1!"), null));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Passing your own username still requires the old password")
    void changeOwnPassword_byOwnUsername_stillRequiresOldPassword() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(child);
        when(userRepository.getRequiredByUsernameAndTenantId("child", TENANT.getId())).thenReturn(child);

        assertThrows(InvalidPasswordException.class,
                () -> service.changePassword(new PasswordChangeDto(null, "NewPassword1!"), "child"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Root changing another user's password needs no old password")
    void changePassword_byRootForAnotherUser_doesNotNeedOldPassword() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(root);
        when(userRepository.getRequiredByUsernameAndTenantId("child", TENANT.getId())).thenReturn(child);
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("encoded-new");

        service.changePassword(new PasswordChangeDto(null, "NewPassword1!"), "child");

        verify(accessPolicy).checkCanChangePassword(root, child);
        assertEquals("encoded-new", child.getPassword());
        verify(userRepository).save(child);
    }

    @Test
    @DisplayName("Changing the password of a user you may not manage is denied")
    void changePassword_forUserNotAllowed_isDenied() {
        User stranger = user(3, "stranger");
        when(currentUserProvider.requireCurrentUser()).thenReturn(child);
        when(userRepository.getRequiredByUsernameAndTenantId("stranger", TENANT.getId())).thenReturn(stranger);
        doThrow(new NoAccessException("denied")).when(accessPolicy).checkCanChangePassword(child, stranger);

        assertThrows(NoAccessException.class,
                () -> service.changePassword(new PasswordChangeDto(null, "NewPassword1!"), "stranger"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("A new password violating the policy is rejected")
    void changePassword_violatingPasswordPolicy_isRejected() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(child);
        when(passwordEncoder.matches("old", "encoded-child")).thenReturn(true);
        doThrow(new UserInputNotValidException("policy")).when(userValidator).validatePasswordPolicy("weak");

        assertThrows(UserInputNotValidException.class,
                () -> service.changePassword(new PasswordChangeDto("old", "weak"), null));
        verify(userRepository, never()).save(any());
    }
}
