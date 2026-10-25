package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.InvalidPasswordException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.passwordreset.service.NewPasswordIssuer;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.session.service.SessionService;
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

import static com.example.iamsystem.user.service.UserTestData.SESSION_ID;
import static com.example.iamsystem.user.service.UserTestData.TENANT;
import static com.example.iamsystem.user.service.UserTestData.actor;
import static com.example.iamsystem.user.service.UserTestData.rootUser;
import static com.example.iamsystem.user.service.UserTestData.user;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPasswordServiceTest {

    private static final Long TENANT_ID = TENANT.getId();

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserAccessPolicy accessPolicy;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserPasswordUpdater passwordUpdater;
    @Mock
    private NewPasswordIssuer newPasswordIssuer;
    @Mock
    private SessionService sessionService;

    private UserPasswordService service;
    private User root;
    private User member;

    @BeforeEach
    void setUp() {
        service = new UserPasswordService(userRepository, accessPolicy, currentUserProvider, passwordEncoder,
                passwordUpdater, newPasswordIssuer, sessionService);
        root = rootUser(1, "root");
        member = user(2, "member");
    }

    @Test
    @DisplayName("The correct old password sets your new password and ends your other sessions")
    void changeOwnPassword_withCorrectOldPassword_setsTheNewPassword() {
        givenLoggedIn(member);
        when(passwordEncoder.matches("old", "encoded-member")).thenReturn(true);

        service.changeOwnPassword(new PasswordChangeDto("old", "NewPassword1!"));

        verify(passwordUpdater).updatePassword(member, "NewPassword1!");
        verify(sessionService).revokeOthersOf(2L, SESSION_ID);
    }

    @Test
    @DisplayName("A wrong old password is rejected")
    void changeOwnPassword_withWrongOldPassword_isRejected() {
        givenLoggedIn(member);
        when(passwordEncoder.matches("wrong", "encoded-member")).thenReturn(false);

        assertThrows(InvalidPasswordException.class,
                () -> service.changeOwnPassword(new PasswordChangeDto("wrong", "NewPassword1!")));
        verifyNoInteractions(passwordUpdater, sessionService);
    }

    @Test
    @DisplayName("A new password violating the policy is rejected")
    void changeOwnPassword_violatingPasswordPolicy_isRejected() {
        givenLoggedIn(member);
        when(passwordEncoder.matches("old", "encoded-member")).thenReturn(true);
        doThrow(new UserInputNotValidException("policy")).when(passwordUpdater).updatePassword(member, "weak");

        assertThrows(UserInputNotValidException.class, () -> service.changeOwnPassword(new PasswordChangeDto("old", "weak")));
        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("Resetting another user's password issues them a new one by email")
    void resetPassword_ofAnotherUser_issuesANewPassword() {
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor(root));
        when(userRepository.getRequiredByIdAndTenantId(2L, TENANT_ID)).thenReturn(member);

        service.resetPassword(2L);

        verify(accessPolicy).checkCanResetPassword(actor(root), member);
        verify(newPasswordIssuer).issueNewPassword(member);
    }

    @Test
    @DisplayName("A denied reset issues no new password")
    void resetPassword_whenNotAllowed_issuesNothing() {
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor(member));
        when(userRepository.getRequiredByIdAndTenantId(1L, TENANT_ID)).thenReturn(root);
        doThrow(new NoAccessException("denied")).when(accessPolicy).checkCanResetPassword(actor(member), root);

        assertThrows(NoAccessException.class, () -> service.resetPassword(1L));
        verifyNoInteractions(newPasswordIssuer);
    }

    @Test
    @DisplayName("Resetting the password of a user outside the tenant is not found")
    void resetPassword_outsideTheTenant_isNotFound() {
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor(root));
        when(userRepository.getRequiredByIdAndTenantId(9L, TENANT_ID)).thenThrow(new DataNotFoundException("User not found"));

        assertThrows(DataNotFoundException.class, () -> service.resetPassword(9L));
        verifyNoInteractions(newPasswordIssuer);
    }

    private void givenLoggedIn(User user) {
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor(user));
        when(userRepository.getRequiredByIdAndTenantId(user.getId(), TENANT_ID)).thenReturn(user);
    }
}
