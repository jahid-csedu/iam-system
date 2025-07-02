package com.example.iamsystem.user.password;

import com.example.iamsystem.exception.DataNotFoundException;
import com.example.iamsystem.exception.InvalidPasswordResetOTPException;
import com.example.iamsystem.user.UserRepository;
import com.example.iamsystem.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static com.example.iamsystem.constant.PasswordResetConstants.INVALID_OTP;
import static com.example.iamsystem.constant.PasswordResetConstants.OTP_DOES_NOT_BELONG_TO_USER;
import static com.example.iamsystem.constant.PasswordResetConstants.OTP_HAS_EXPIRED;
import static com.example.iamsystem.constant.PasswordResetConstants.PASSWORD_RESET_REQUEST_BODY_PREFIX;
import static com.example.iamsystem.constant.PasswordResetConstants.PASSWORD_RESET_REQUEST_SUBJECT;
import static com.example.iamsystem.constant.PasswordResetConstants.PASSWORD_RESET_SUCCESS_BODY_PREFIX;
import static com.example.iamsystem.constant.PasswordResetConstants.PASSWORD_RESET_SUCCESS_SUBJECT;
import static com.example.iamsystem.constant.PasswordResetConstants.USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetOTPRepository otpRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    @Value("${password.reset.otp.expiration.minutes}")
    private int expiryTimeInMinutes;

    public void createPasswordResetTokenForUser(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));

        // Delete any existing OTP for this user
        PasswordResetOTP existingOtp = otpRepository.findByUser(user);
        if (existingOtp != null) {
            otpRepository.delete(existingOtp);
        }

        String otp = generateOTP();
        PasswordResetOTP myOtp = new PasswordResetOTP();
        myOtp.setUser(user);
        myOtp.setOtp(otp);
        myOtp.setExpiryDate(calculateExpiryDate(expiryTimeInMinutes));
        otpRepository.save(myOtp);

        SimpleMailMessage emailMessage = new SimpleMailMessage();
        emailMessage.setTo(user.getEmail());
        emailMessage.setSubject(PASSWORD_RESET_REQUEST_SUBJECT);
        emailMessage.setText(PASSWORD_RESET_REQUEST_BODY_PREFIX + otp);
        mailSender.send(emailMessage);
    }

    public void resetPassword(String otp, String email) {
        PasswordResetOTP resetOtp = otpRepository.findByOtp(otp);
        if (resetOtp == null) {
            throw new InvalidPasswordResetOTPException(INVALID_OTP);
        }

        if (!resetOtp.getUser().getEmail().equals(email)) {
            throw new InvalidPasswordResetOTPException(OTP_DOES_NOT_BELONG_TO_USER);
        }

        if (resetOtp.getExpiryDate().before(new Date())) {
            throw new InvalidPasswordResetOTPException(OTP_HAS_EXPIRED);
        }

        User user = resetOtp.getUser();
        String newPassword = generateSecurePassword();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        SimpleMailMessage emailMessage = new SimpleMailMessage();
        emailMessage.setTo(user.getEmail());
        emailMessage.setSubject(PASSWORD_RESET_SUCCESS_SUBJECT);
        emailMessage.setText(PASSWORD_RESET_SUCCESS_BODY_PREFIX + newPassword);
        mailSender.send(emailMessage);

        otpRepository.delete(resetOtp);
    }

    private String generateOTP() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000); // Generates a 6-digit number
        return String.valueOf(otp);
    }

    private static final String UPPER_CASE_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER_CASE_LETTERS = "abcdefghijklmnopqrstuvwxyz";
    private static final String NUMBERS = "0123456789";
    private static final String SPECIAL_CHARS = "@#!$%^&-+=()";
    private static final String ALL_CHARS = UPPER_CASE_LETTERS + LOWER_CASE_LETTERS + NUMBERS + SPECIAL_CHARS;

    private String generateSecurePassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();

        // Ensure at least one of each required character type
        password.append(UPPER_CASE_LETTERS.charAt(random.nextInt(UPPER_CASE_LETTERS.length())));
        password.append(LOWER_CASE_LETTERS.charAt(random.nextInt(LOWER_CASE_LETTERS.length())));
        password.append(NUMBERS.charAt(random.nextInt(NUMBERS.length())));
        password.append(SPECIAL_CHARS.charAt(random.nextInt(SPECIAL_CHARS.length())));

        // Fill the rest of the password with random characters
        for (int i = 4; i < 12; i++) {
            password.append(ALL_CHARS.charAt(random.nextInt(ALL_CHARS.length())));
        }

        // Shuffle the password to randomize the character positions
        List<Character> passwordChars = password.chars().mapToObj(c -> (char) c).collect(Collectors.toList());
        Collections.shuffle(passwordChars, random);

        return passwordChars.stream().collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString();
    }

    private Date calculateExpiryDate(int expiryTimeInMinutes) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.MINUTE, expiryTimeInMinutes);
        return new Date(cal.getTime().getTime());
    }
}
