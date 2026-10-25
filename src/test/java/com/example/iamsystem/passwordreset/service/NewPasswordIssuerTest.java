package com.example.iamsystem.passwordreset.service;

import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.session.service.SessionService;
import com.example.iamsystem.user.config.PasswordExpirationProperties;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewPasswordIssuerTest {

    private static final String PASSWORD_POLICY = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#!$%^&\\-+=()]).{12}$";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private SessionService sessionService;

    private NewPasswordIssuer issuer;
    private User user;

    @BeforeEach
    void setUp() {
        issuer = new NewPasswordIssuer(userRepository, passwordEncoder, mailSender, new PasswordExpirationProperties(90), sessionService);
        user = new User();
        user.setId(7L);
        user.setEmail("alice@example.com");
    }

    @Test
    @DisplayName("A new policy-compliant password is stored encoded, sent only to the user's email, and ends every session")
    void issueNewPassword_storesEncodedAndEmailsIt() {
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "encoded:" + invocation.getArgument(0));

        issuer.issueNewPassword(user);

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        String password = mail.getValue().getText().substring(mail.getValue().getText().lastIndexOf(' ') + 1);
        assertTrue(password.matches(PASSWORD_POLICY), password);
        assertArrayEquals(new String[]{"alice@example.com"}, mail.getValue().getTo());
        assertEquals("encoded:" + password, user.getPassword());
        assertTrue(user.getPasswordExpiryDate().isAfter(Instant.now()));
        verify(userRepository).save(user);
        verify(sessionService).revokeAllOf(7L);
    }

    @Test
    @DisplayName("Every issued password is different")
    void issueNewPassword_generatesDifferentPasswords() {
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        issuer.issueNewPassword(user);
        String first = user.getPassword();
        issuer.issueNewPassword(user);

        assertNotEquals(first, user.getPassword());
    }

    @Test
    @DisplayName("A user without an email address gets no new password")
    void issueNewPassword_withoutEmail_isRejected() {
        user.setEmail(null);

        assertThrows(UserInputNotValidException.class, () -> issuer.issueNewPassword(user));
        verifyNoInteractions(passwordEncoder, userRepository, mailSender, sessionService);
    }
}
