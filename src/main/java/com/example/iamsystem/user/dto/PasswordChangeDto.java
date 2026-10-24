package com.example.iamsystem.user.dto;

import jakarta.validation.constraints.NotEmpty;

public record PasswordChangeDto(
        String oldPassword,
        @NotEmpty
        String newPassword
) {
}