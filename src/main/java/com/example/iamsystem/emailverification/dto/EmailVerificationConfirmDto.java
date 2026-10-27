package com.example.iamsystem.emailverification.dto;

import com.example.iamsystem.common.util.SecretMask;
import jakarta.validation.constraints.NotBlank;

/**
 * The code from the verification email. Never shown by {@link #toString()}.
 */
public record EmailVerificationConfirmDto(@NotBlank String code) {

    @Override
    public String toString() {
        return "EmailVerificationConfirmDto[code=" + SecretMask.MASK + "]";
    }
}
