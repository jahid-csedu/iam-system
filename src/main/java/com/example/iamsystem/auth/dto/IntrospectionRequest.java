package com.example.iamsystem.auth.dto;

import jakarta.validation.constraints.NotBlank;

import static com.example.iamsystem.common.exception.ErrorMessage.TOKEN_REQUIRED;

public record IntrospectionRequest(@NotBlank(message = TOKEN_REQUIRED) String token) {
}
