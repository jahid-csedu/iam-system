package com.example.iamsystem.client.dto;

import jakarta.validation.constraints.NotBlank;

import static com.example.iamsystem.common.exception.ErrorMessage.CLIENT_NAME_REQUIRED;

/**
 * Registers or renames an application client; names are unique within the tenant.
 */
public record ClientRequestDto(@NotBlank(message = CLIENT_NAME_REQUIRED) String name) {
}
