package com.example.iamsystem.user.model.dto;

import jakarta.validation.constraints.NotEmpty;

public record PasswordChangeDto(
        String oldPassword,
        @NotEmpty
        String newPassword
) {
}