package com.example.iamsystem.user.service;

import com.example.iamsystem.authorization.AuthorizationService;
import com.example.iamsystem.common.exception.InvalidPasswordException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.common.exception.UserAlreadyExistsException;
import com.example.iamsystem.passwordreset.service.ResetOtpIssuer;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.user.dto.OwnProfileUpdateDto;
import com.example.iamsystem.user.dto.UserDto;
import com.example.iamsystem.user.dto.UserUpdateDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import com.example.iamsystem.user.mapper.UserMapper;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static com.example.iamsystem.permission.PermissionKey.USERS_RESET_PASSWORD;
import static com.example.iamsystem.user.service.UserTestData.TENANT;
import static com.example.iamsystem.user.service.UserTestData.actor;
import static com.example.iamsystem.user.service.UserTestData.rootUser;
import static com.example.iamsystem.user.service.UserTestData.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserAccessPolicy accessPolicy;
    @Mock
    private UserValidator userValidator;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private ResetOtpIssuer resetOtpIssuer;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private UserProfileService service;
    private AuthenticatedActor manager;
    private User bob;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(userRepository, accessPolicy, userValidator, authorizationService, resetOtpIssuer,
                currentUserProvider, passwordEncoder, Mappers.getMapper(UserMapper.class));
        manager = actor(user(1, "manager"));
        bob = user(2, "bob");
        bob.setFullName("Bob");
        lenient().when(currentUserProvider.requireCurrentActor()).thenReturn(manager);
        lenient().when(userRepository.getRequiredByIdAndTenantId(2L, TENANT.getId())).thenReturn(bob);
    }

    @Test
    @DisplayName("A new email needs users:reset-password and a free address, and discards the pending reset OTP")
    void updateUser_newEmail_isCheckedAndDiscardsThePendingOtp() {
        when(userRepository.save(bob)).thenReturn(bob);

        UserDto updated = service.updateUser(2L, new UserUpdateDto(null, "robert@example.com"));

        verify(accessPolicy).checkCanUpdateProfile(manager, bob);
        verify(authorizationService).checkPermission(manager, USERS_RESET_PASSWORD);
        verify(userValidator).validateEmailAvailable("robert@example.com", TENANT.getId());
        verify(resetOtpIssuer).discardPending(bob);
        assertEquals("robert@example.com", updated.getEmail());
        assertEquals("Bob", updated.getFullName());
    }

    @Test
    @DisplayName("A name change needs nothing more, and an empty name removes it")
    void updateUser_nameOnly_needsNoMoreChecks() {
        when(userRepository.save(bob)).thenReturn(bob);

        assertEquals("Robert", service.updateUser(2L, new UserUpdateDto("Robert", null)).getFullName());
        assertNull(service.updateUser(2L, new UserUpdateDto("", null)).getFullName());
        assertEquals("bob@example.com", bob.getEmail());
        verifyNoInteractions(authorizationService, userValidator, resetOtpIssuer);
    }

    @Test
    @DisplayName("The same email in other letter case is no new address: only its spelling is updated")
    void updateUser_sameEmailOtherCase_isNoNewAddress() {
        when(userRepository.save(bob)).thenReturn(bob);

        assertEquals("Bob@Example.com", service.updateUser(2L, new UserUpdateDto(null, "Bob@Example.com")).getEmail());
        verifyNoInteractions(authorizationService, userValidator, resetOtpIssuer);
    }

    @Test
    @DisplayName("Without users:reset-password the email stays unchanged and nothing is saved")
    void updateUser_newEmailWithoutResetPermission_isRefused() {
        doThrow(new NoAccessException("Access Denied")).when(authorizationService).checkPermission(manager, USERS_RESET_PASSWORD);

        assertThrows(NoAccessException.class, () -> service.updateUser(2L, new UserUpdateDto("Robert", "robert@example.com")));
        assertEquals("bob@example.com", bob.getEmail());
        assertEquals("Bob", bob.getFullName());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(resetOtpIssuer);
    }

    @Test
    @DisplayName("A taken email is refused and the pending OTP is kept")
    void updateUser_takenEmail_isRefused() {
        doThrow(new UserAlreadyExistsException("Email not available"))
                .when(userValidator).validateEmailAvailable("alice@example.com", TENANT.getId());

        assertThrows(UserAlreadyExistsException.class, () -> service.updateUser(2L, new UserUpdateDto(null, "alice@example.com")));
        verifyNoInteractions(resetOtpIssuer);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Your own new email needs your current password and a free address in your tenant, and discards your pending OTP")
    void updateOwnProfile_newEmail_needsTheCurrentPassword() {
        User alice = ownAccount(user(3, "alice"));

        service.updateOwnProfile(new OwnProfileUpdateDto("Alice", "new@example.com", "Password1!"));

        verify(accessPolicy).checkIsManagementUser(alice);
        verify(userValidator).validateEmailAvailable("new@example.com", TENANT.getId());
        verify(resetOtpIssuer).discardPending(alice);
        verify(userRepository).save(alice);
        assertEquals("new@example.com", alice.getEmail());
        assertEquals("Alice", alice.getFullName());
    }

    @Test
    @DisplayName("A missing or wrong current password refuses your new email and changes nothing")
    void updateOwnProfile_newEmailWithoutTheRightPassword_isRefused() {
        User alice = ownAccount(user(3, "alice"));

        for (String password : new String[]{null, "Wrong1!"}) {
            InvalidPasswordException refused = assertThrows(InvalidPasswordException.class,
                    () -> service.updateOwnProfile(new OwnProfileUpdateDto("Alice", "new@example.com", password)));
            assertEquals("Changing your email needs your current password, and it was missing or wrong", refused.getMessage());
        }
        assertEquals("alice@example.com", alice.getEmail());
        verifyNoInteractions(userValidator, resetOtpIssuer);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("A root's own new email must be unused by every user of every tenant")
    void updateOwnProfile_rootsNewEmail_isCheckedAcrossTenants() {
        ownAccount(rootUser(3, "root"));

        service.updateOwnProfile(new OwnProfileUpdateDto(null, "new@example.com", "Password1!"));

        verify(userValidator).validateEmailAvailableForRoot("new@example.com");
        verify(userValidator, never()).validateEmailAvailable(any(), any());
    }

    @Test
    @DisplayName("Your own name changes without a password")
    void updateOwnProfile_nameOnly_needsNoPassword() {
        User alice = ownAccount(user(3, "alice"));

        service.updateOwnProfile(new OwnProfileUpdateDto("Alice Admin", null, null));

        assertEquals("Alice Admin", alice.getFullName());
        verifyNoInteractions(userValidator, resetOtpIssuer);
    }

    @Test
    @DisplayName("A regular user can't change their own profile here")
    void updateOwnProfile_regularUser_isRefused() {
        User alice = ownAccount(user(3, "alice"));
        doThrow(new NoAccessException("Only management users can use this endpoint")).when(accessPolicy).checkIsManagementUser(alice);

        assertThrows(NoAccessException.class, () -> service.updateOwnProfile(new OwnProfileUpdateDto("Alice", null, null)));
        verify(userRepository, never()).save(any());
    }

    /**
     * The given user, logged in as themselves, with the password "Password1!".
     */
    private User ownAccount(User user) {
        user.setUserType(UserType.MANAGEMENT);
        user.setPassword(passwordEncoder.encode("Password1!"));
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor(user));
        when(userRepository.getRequiredByIdAndTenantId(user.getId(), TENANT.getId())).thenReturn(user);
        return user;
    }

    @Test
    @DisplayName("A user the policy protects (yourself, the root) is refused before anything else")
    void updateUser_protectedTarget_isRefused() {
        doThrow(new NoAccessException("Only the root user can change their own profile")).when(accessPolicy).checkCanUpdateProfile(manager, bob);

        assertThrows(NoAccessException.class, () -> service.updateUser(2L, new UserUpdateDto("Robert", "robert@example.com")));
        verifyNoInteractions(authorizationService, userValidator, resetOtpIssuer);
    }
}
