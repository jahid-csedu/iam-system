package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.UserAlreadyExistsException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserValidatorTest {

    private static final Long TENANT_ID = 100L;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserValidator validator;

    @Test
    @DisplayName("A username and email free in the tenant and not used by any root are available")
    void validateAvailable_freeInTenant_isAccepted() {
        assertDoesNotThrow(() -> validator.validateAvailable("alice", "alice@example.com", TENANT_ID));
    }

    @Test
    @DisplayName("A username taken in the same tenant is rejected")
    void validateAvailable_usernameTakenInTenant_isRejected() {
        when(userRepository.existsByUsernameAndTenantId("alice", TENANT_ID)).thenReturn(true);

        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class,
                () -> validator.validateAvailable("alice", "alice@example.com", TENANT_ID));
        assertEquals("Username not available", exception.getMessage());
    }

    @Test
    @DisplayName("An email taken in the same tenant is rejected")
    void validateAvailable_emailTakenInTenant_isRejected() {
        when(userRepository.existsByEmailAndTenantId("alice@example.com", TENANT_ID)).thenReturn(true);

        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class,
                () -> validator.validateAvailable("alice", "alice@example.com", TENANT_ID));
        assertEquals("Email not available", exception.getMessage());
    }

    @Test
    @DisplayName("A root user's username or email is rejected in every tenant")
    void validateAvailable_rootsUsernameOrEmail_isRejected() {
        when(userRepository.existsByUsernameAndIsRootUserTrue("acme-root")).thenReturn(true);
        when(userRepository.existsByEmailAndIsRootUserTrue("root@acme.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> validator.validateAvailable("acme-root", null, TENANT_ID));
        assertThrows(UserAlreadyExistsException.class, () -> validator.validateAvailable("alice", "root@acme.com", TENANT_ID));
    }

    @Test
    @DisplayName("A user without an email only needs a free username")
    void validateAvailable_withoutEmail_checksOnlyTheUsername() {
        assertDoesNotThrow(() -> validator.validateAvailable("alice", null, TENANT_ID));
    }

    @Test
    @DisplayName("A non-root user's new email must be free in their tenant and not belong to any tenant's root")
    void validateEmailAvailable_checksTheTenantAndAllRoots() {
        when(userRepository.existsByEmailAndTenantId("bob@example.com", TENANT_ID)).thenReturn(true);
        when(userRepository.existsByEmailAndIsRootUserTrue("root@globex.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> validator.validateEmailAvailable("bob@example.com", TENANT_ID));
        assertThrows(UserAlreadyExistsException.class, () -> validator.validateEmailAvailable("root@globex.com", TENANT_ID));
        assertDoesNotThrow(() -> validator.validateEmailAvailable("new@example.com", TENANT_ID));
    }

    @Test
    @DisplayName("A root user's new email must be unused by every user of every tenant")
    void validateEmailAvailableForRoot_checksAllTenants() {
        when(userRepository.existsByEmail("bob@globex.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> validator.validateEmailAvailableForRoot("bob@globex.com"));
        assertDoesNotThrow(() -> validator.validateEmailAvailableForRoot("new@example.com"));
    }

    @Test
    @DisplayName("A new root user needs a username and email unused by any user of any tenant")
    void validateAvailableForRoot_checksAllTenants() {
        when(userRepository.existsByUsername("bob")).thenReturn(true);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> validator.validateAvailableForRoot("bob", "new@example.com"));
        assertThrows(UserAlreadyExistsException.class, () -> validator.validateAvailableForRoot("new-root", "bob@example.com"));
        assertDoesNotThrow(() -> validator.validateAvailableForRoot("new-root", "new@example.com"));
    }

    @Test
    @DisplayName("Passwords must meet the policy")
    void validatePasswordPolicy_enforcesThePolicy() {
        assertDoesNotThrow(() -> validator.validatePasswordPolicy("Password1!"));
        assertThrows(UserInputNotValidException.class, () -> validator.validatePasswordPolicy("password"));
        assertThrows(UserInputNotValidException.class, () -> validator.validatePasswordPolicy("Pass1!"));
    }

    @Test
    @DisplayName("Every listed special character counts, the hyphen included, and no other character does")
    void validatePasswordPolicy_acceptsExactlyTheListedSpecialCharacters() {
        for (char special : "@#!$%^&-+=()".toCharArray()) {
            assertDoesNotThrow(() -> validator.validatePasswordPolicy("Password1" + special));
        }
        for (char other : "'*,._?".toCharArray()) {
            assertThrows(UserInputNotValidException.class, () -> validator.validatePasswordPolicy("Password1" + other));
        }
    }
}
