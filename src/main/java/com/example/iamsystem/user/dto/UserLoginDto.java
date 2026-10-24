package com.example.iamsystem.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import static com.example.iamsystem.common.exception.ErrorMessage.CLIENT_ID_REQUIRED;
import static com.example.iamsystem.common.exception.ErrorMessage.PASSWORD_REQUIRED;
import static com.example.iamsystem.common.exception.ErrorMessage.USERNAME_REQUIRED;

/**
 * A login through a client of the tenant; the client decides which tenant the username belongs to.
 */
public record UserLoginDto(@JsonProperty("client_id") @NotBlank(message = CLIENT_ID_REQUIRED) String clientId,
                           @NotBlank(message = USERNAME_REQUIRED) String username,
                           @NotBlank(message = PASSWORD_REQUIRED) String password) {
}
