package com.example.iamsystem.passwordreset.service;

import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.user.config.PasswordExpirationProperties;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.example.iamsystem.common.exception.ErrorMessage.USER_HAS_NO_EMAIL;
import static com.example.iamsystem.common.util.DateUtil.calculateExpiryDate;
import static com.example.iamsystem.passwordreset.PasswordResetConstants.PASSWORD_RESET_SUCCESS_BODY_PREFIX;
import static com.example.iamsystem.passwordreset.PasswordResetConstants.PASSWORD_RESET_SUCCESS_SUBJECT;

/**
 * Gives a user a new random password and sends it to the user's email address, for both password resets
 * (self-service with OTP, and by another user). The password is only ever sent by email: never returned,
 * logged or audited. Saving the user also revokes the tokens issued before the reset.
 */
@Component
@RequiredArgsConstructor
public class NewPasswordIssuer {

    private static final String UPPER_CASE_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER_CASE_LETTERS = "abcdefghijklmnopqrstuvwxyz";
    private static final String NUMBERS = "0123456789";
    private static final String SPECIAL_CHARS = "@#!$%^&-+=()";
    private static final String ALL_CHARS = UPPER_CASE_LETTERS + LOWER_CASE_LETTERS + NUMBERS + SPECIAL_CHARS;
    private static final int PASSWORD_LENGTH = 12;

    private final SecureRandom random = new SecureRandom();
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final PasswordExpirationProperties passwordExpiration;

    /**
     * Must run inside the caller's transaction, so a failed email also rolls back the password change.
     */
    public void issueNewPassword(User user) {
        if (user.getEmail() == null) {
            throw new UserInputNotValidException(USER_HAS_NO_EMAIL);
        }
        String password = generatePassword();
        user.setPassword(passwordEncoder.encode(password));
        user.setPasswordExpiryDate(calculateExpiryDate(passwordExpiration.days()));
        userRepository.save(user);

        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(user.getEmail());
        email.setSubject(PASSWORD_RESET_SUCCESS_SUBJECT);
        email.setText(PASSWORD_RESET_SUCCESS_BODY_PREFIX + password);
        mailSender.send(email);
    }

    /**
     * Satisfies the password policy: at least one upper-case letter, lower-case letter, digit and special character.
     */
    private String generatePassword() {
        List<Character> chars = new ArrayList<>(PASSWORD_LENGTH);
        chars.add(randomCharOf(UPPER_CASE_LETTERS));
        chars.add(randomCharOf(LOWER_CASE_LETTERS));
        chars.add(randomCharOf(NUMBERS));
        chars.add(randomCharOf(SPECIAL_CHARS));
        while (chars.size() < PASSWORD_LENGTH) {
            chars.add(randomCharOf(ALL_CHARS));
        }
        Collections.shuffle(chars, random);

        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        chars.forEach(password::append);
        return password.toString();
    }

    private char randomCharOf(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
