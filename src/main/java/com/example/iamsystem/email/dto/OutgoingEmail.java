package com.example.iamsystem.email.dto;

import com.example.iamsystem.common.util.SecretMask;

import java.util.Map;

/**
 * One email to send: its purpose, the recipient and the values its templates need. The values may hold a secret (an
 * OTP), so {@link #toString()} never shows them.
 */
public record OutgoingEmail(EmailPurpose purpose, String to, Map<String, Object> values) {

    public OutgoingEmail {
        values = Map.copyOf(values);
    }

    @Override
    public String toString() {
        return "OutgoingEmail[purpose=" + purpose + ", to=" + to + ", values=" + SecretMask.MASK + "]";
    }
}
