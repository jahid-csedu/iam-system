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
        User user = userRepository.findByEmail(email).orElseThrow(() -> new DataNotFoundException("User not found"));

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
        emailMessage.setSubject("Password Reset Request");
        emailMessage.setText("To reset your password, use the following OTP: " + otp);
        mailSender.send(emailMessage);
    }

    public void resetPassword(String otp, String email) {
        PasswordResetOTP resetOtp = otpRepository.findByOtp(otp);
        if (resetOtp == null) {
            throw new InvalidPasswordResetOTPException("Invalid OTP");
        }

        if (!resetOtp.getUser().getEmail().equals(email)) {
            throw new InvalidPasswordResetOTPException("OTP does not belong to this user");
        }

        if (resetOtp.getExpiryDate().before(new Date())) {
            throw new InvalidPasswordResetOTPException("OTP has expired");
        }

        User user = resetOtp.getUser();
        String newPassword = generateSecurePassword();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        SimpleMailMessage emailMessage = new SimpleMailMessage();
        emailMessage.setTo(user.getEmail());
        emailMessage.setSubject("Password Reset");
        emailMessage.setText("Your new password is: " + newPassword);
        mailSender.send(emailMessage);

        otpRepository.delete(resetOtp);
    }

    private String generateOTP() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000); // Generates a 6-digit number
        return String.valueOf(otp);
    }

    private String generateSecurePassword() {
        String upperCaseLetters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowerCaseLetters = "abcdefghijklmnopqrstuvwxyz";
        String numbers = "0123456789";
        String specialChars = "@#!$%^&-+=()";

        String allChars = upperCaseLetters + lowerCaseLetters + numbers + specialChars;

        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();

        // Ensure at least one of each required character type
        password.append(upperCaseLetters.charAt(random.nextInt(upperCaseLetters.length())));
        password.append(lowerCaseLetters.charAt(random.nextInt(lowerCaseLetters.length())));
        password.append(numbers.charAt(random.nextInt(numbers.length())));
        password.append(specialChars.charAt(random.nextInt(specialChars.length())));

        // Fill the rest of the password with random characters
        for (int i = 4; i < 12; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
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
