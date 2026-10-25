package com.example.iamsystem.user.dto;

import com.example.iamsystem.common.exception.ErrorMessage;
import com.example.iamsystem.common.validation.ValidEmail;
import com.example.iamsystem.user.entity.UserType;
import jakarta.validation.constraints.NotBlank;

import static com.example.iamsystem.common.util.SecretMask.MASK;

/**
 * Details of a new user. Password strength is checked against the password policy by {@code UserValidator}.
 * {@code userType} is optional ({@code REGULAR} by default) and ignored at tenant signup, where the root user is always
 * a management user.
 */
public record UserCreationDto(
        @NotBlank(message = ErrorMessage.USERNAME_REQUIRED)
        String username,

        @NotBlank(message = ErrorMessage.PASSWORD_REQUIRED)
        String password,

        String fullName,

        @ValidEmail(message = ErrorMessage.INVALID_EMAIL)
        String email,

        UserType userType
) {

    /**
     * Never shows the secret, so logging the request is safe.
     */
    @Override
    public String toString() {
        return "UserCreationDto[username=" + username + ", password=" + MASK + ", fullName=" + fullName
                + ", email=" + email + ", userType=" + userType + "]";
    }
}
