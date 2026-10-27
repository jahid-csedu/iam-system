package com.example.iamsystem.otp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OneTimeCodesTest {

    private final OneTimeCodes oneTimeCodes = new OneTimeCodes();

    @Test
    @DisplayName("A code is always six digits, leading zeros included, and codes vary")
    void newCode_isSixRandomDigits() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String code = oneTimeCodes.newCode();
            assertTrue(code.matches("\\d{6}"), code);
            codes.add(code);
        }
        assertTrue(codes.size() > 190, "codes repeat too often: " + codes.size());
    }
}
