package com.example.iamsystem.otp.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * What every one-time code shares: six random digits from {@link SecureRandom}, stored only as a bcrypt hash by its
 * feature (six digits are too few for a fast hash), and deleted after {@value #MAX_FAILED_ATTEMPTS} wrong guesses.
 */
@Component
public class OneTimeCodes {

    /**
     * Wrong guesses allowed against one code; the last one deletes it, and the user has to request a new one.
     */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    private static final int CODE_BOUND = 1_000_000;
    private static final String CODE_FORMAT = "%06d";

    private final SecureRandom random = new SecureRandom();

    public String newCode() {
        return String.format(CODE_FORMAT, random.nextInt(CODE_BOUND));
    }
}
