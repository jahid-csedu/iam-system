package com.example.iamsystem.user.dto;

import com.example.iamsystem.common.exception.ErrorMessage;
import com.example.iamsystem.common.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;

/**
 * Details of a new user. Password strength is checked against the password policy by {@code UserValidator}.
 */
public record UserCreationDto(
        @NotBlank(message = ErrorMessage.USERNAME_REQUIRED)
        String username,

        @NotBlank(message = ErrorMessage.PASSWORD_REQUIRED)
        String password,

        String fullName,

        @ValidEmail(message = ErrorMessage.INVALID_EMAIL)
        String email
) {
}
