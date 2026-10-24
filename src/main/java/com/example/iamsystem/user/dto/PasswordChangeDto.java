package com.example.iamsystem.user.dto;

import jakarta.validation.constraints.NotBlank;

import static com.example.iamsystem.common.exception.ErrorMessage.NEW_PASSWORD_REQUIRED;
import static com.example.iamsystem.common.exception.ErrorMessage.OLD_PASSWORD_REQUIRED;

public record PasswordChangeDto(
        @NotBlank(message = OLD_PASSWORD_REQUIRED) String oldPassword,
        @NotBlank(message = NEW_PASSWORD_REQUIRED) String newPassword
) {
}
