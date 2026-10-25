package com.example.iamsystem.common.util;

import com.example.iamsystem.auth.dto.IntrospectionRequest;
import com.example.iamsystem.auth.dto.JwtRefreshTokenDto;
import com.example.iamsystem.passwordreset.dto.PasswordResetDto;
import com.example.iamsystem.passwordreset.entity.PasswordResetOTP;
import com.example.iamsystem.platform.config.PlatformProperties.RootUser;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.user.dto.PasswordChangeDto;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserLoginDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretMaskTest {

    private static final String SECRET = "S3cret-value!";

    @Test
    @DisplayName("Requests and entities carrying a password, OTP or token never show it in toString, but keep the other fields")
    void credentialCarryingRecords_maskTheSecret() {
        List<Object> requests = List.of(
                new UserLoginDto("client-1", "alice", SECRET),
                new UserCreationDto("alice", SECRET, "Alice", "alice@acme.com", null),
                new TenantSignupDto("Acme", new UserCreationDto("alice", SECRET, "Alice", "alice@acme.com", null)),
                new PasswordChangeDto(SECRET, SECRET),
                new PasswordResetDto("client-1", "alice", SECRET),
                new JwtRefreshTokenDto("client-1", SECRET),
                new IntrospectionRequest(SECRET),
                new RootUser("alice", SECRET, "alice@acme.com", null),
                pendingOtp());

        assertAll(requests.stream().map(request -> () -> {
            String text = request.toString();
            assertFalse(text.contains(SECRET), text);
            assertTrue(text.contains(SecretMask.MASK), text);
        }));
        assertTrue(new UserLoginDto("client-1", "alice", SECRET).toString().contains("alice"));
    }

    private static PasswordResetOTP pendingOtp() {
        PasswordResetOTP otp = new PasswordResetOTP();
        otp.setOtp(SECRET);
        return otp;
    }
}
