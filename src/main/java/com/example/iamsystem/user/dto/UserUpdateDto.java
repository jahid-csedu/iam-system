package com.example.iamsystem.user.dto;

import com.example.iamsystem.common.exception.ErrorMessage;
import com.example.iamsystem.common.validation.ValidEmail;

import java.util.ArrayList;
import java.util.List;

/**
 * Changes to a user's profile. A field left out (or {@code null}) stays as it is; an empty {@code fullName} removes the
 * name. An email can be changed but not removed, because password resets are sent to it.
 */
public record UserUpdateDto(String fullName,
                            @ValidEmail(message = ErrorMessage.INVALID_EMAIL) String email) {

    /**
     * The names of the fields this request changes, for the audit log (never their values).
     */
    public List<String> fields() {
        List<String> fields = new ArrayList<>();
        if (fullName != null) {
            fields.add("fullName");
        }
        if (email != null) {
            fields.add("email");
        }
        return fields;
    }
}
