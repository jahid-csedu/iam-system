package com.example.iamsystem.user.dto;

import com.example.iamsystem.common.exception.ErrorMessage;
import com.example.iamsystem.common.validation.ValidEmail;

import static com.example.iamsystem.common.util.SecretMask.MASK;

/**
 * Changes to your own profile: the same fields and rules as {@link UserUpdateDto}, plus your current password, which a
 * new email address needs (a stolen access token alone must not redirect your password resets).
 */
public record OwnProfileUpdateDto(String fullName,
                                  @ValidEmail(message = ErrorMessage.INVALID_EMAIL) String email,
                                  String currentPassword) {

    public UserUpdateDto changes() {
        return new UserUpdateDto(fullName, email);
    }

    /**
     * Never shows the secret, so logging the request is safe.
     */
    @Override
    public String toString() {
        return "OwnProfileUpdateDto[fullName=" + fullName + ", email=" + email + ", currentPassword=" + MASK + "]";
    }
}
